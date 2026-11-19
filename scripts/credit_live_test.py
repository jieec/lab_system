"""Credit recovery and asset identity checks against the guarded disposable DB."""
import json,os,subprocess,uuid
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
import agent_live_test as live

settings=dict(line.split('=',1) for line in Path('.env').read_text(encoding='utf-8').splitlines() if '=' in line and not line.startswith('#'))
os.environ['MYSQL_PWD']=settings['MYSQL_PASSWORD']
def sql(query):
    r=subprocess.run(['mysql','-h127.0.0.1','-uroot','--default-character-set=utf8mb4','--batch','--skip-column-names',live.DATABASE,'-e',query],capture_output=True,text=True)
    if r.returncode: raise AssertionError('Isolated SQL fixture operation failed')
    return r.stdout.strip()
def main():
    admin=live.login('admin')
    name='credit_test_'+uuid.uuid4().hex[:8]
    live.ok('POST','/users',admin,{'username':name,'realName':'信用恢复演示用户','role':'STUDENT'})
    login=live.ok('POST','/auth/login',body={'username':name,'password':'123456'})
    user=login['user'];token=login['token'];uid=user['id']
    live.ok('POST','/credit/adjust',admin,{'userId':uid,'targetScore':55,'reason':'隔离测试：模拟低分用户，验证恢复流程'})
    me=live.ok('GET','/credit/me',token)
    live.check('Below 60 cannot reserve',me['score']==55 and not me['canReserve'])
    live.check('Student cannot directly raise credit',live.call('POST','/credit/adjust',token,{'userId':uid,'targetScore':100,'reason':'自行恢复不允许'})['code']==403)
    live.check('Short recovery explanation rejected',live.call('POST','/credit/appeals',token,{'reason':'恢复'})['code']!=200)
    appeal=live.ok('POST','/credit/appeals',token,{'reason':'此前因时间安排失误未履约，已重新学习预约规则，今后提前取消或准时签到。'})
    live.check('Recovery request enters pending state',appeal['status']=='PENDING')
    live.check('Duplicate pending request blocked',live.call('POST','/credit/appeals',token,{'reason':'再次提交申请，请核查恢复我的信用分。'})['code']!=200)
    live.check('Student cannot view global review queue',live.call('GET','/credit/appeals?pending=true',token)['code']==403)
    live.check('Student cannot approve recovery',live.call('POST',f"/credit/appeals/{appeal['id']}/review",token,{'approve':True,'reason':'自行审核不允许'})['code']==403)
    queue=live.ok('GET','/credit/appeals?pending=true',admin)['records']
    live.check('Admin sees pending recovery',any(row['id']==appeal['id'] for row in queue))
    reviewed=live.ok('POST',f"/credit/appeals/{appeal['id']}/review",admin,{'approve':True,'reason':'已核实情况与改进措施，同意恢复预约资格'})
    me=live.ok('GET','/credit/me',token)
    live.check('Approval restores to 70 and reopens eligible account',reviewed['status']=='APPROVED' and me['score']==70 and me['canReserve'])
    live.check('Repeated review cannot apply twice',live.call('POST',f"/credit/appeals/{appeal['id']}/review",admin,{'approve':True,'reason':'再次审核应该被拒绝'})['code']!=200)
    ledger=live.ok('GET','/credit/records',token)['records']
    live.check('Recovery has exact before/after ledger',any(r['scoreBefore']==55 and r['scoreAfter']==70 and r['changeAmount']==15 for r in ledger))
    live.ok('POST','/credit/adjust',admin,{'userId':uid,'targetScore':50,'reason':'隔离测试：验证七天内恢复次数限制'})
    live.check('Seven-day recovery cooldown enforced',live.call('POST','/credit/appeals',token,{'reason':'再次申请信用恢复，测试是否正确限制频率。'})['code']!=200)
    live.ok('POST','/credit/adjust',admin,{'userId':uid,'targetScore':99,'reason':'隔离测试：验证满分上限与实际加分'})
    lab=live.ok('GET','/labs/all',admin)[0]
    marker='CREDIT-CHECK-'+uuid.uuid4().hex[:8]
    sql(f"INSERT INTO reservation(order_no,user_id,lab_id,start_time,end_time,headcount,purpose,status,checkin_status,create_time) VALUES('{marker}',{uid},{lab['id']},DATE_SUB(NOW(),INTERVAL 1 MINUTE),DATE_ADD(NOW(),INTERVAL 1 HOUR),1,'隔离签到测试','APPROVED','NONE',NOW())")
    rid=int(sql(f"SELECT id FROM reservation WHERE order_no='{marker}'"))
    live.ok('PUT',f'/reservations/{rid}/checkin',token)
    live.check('Check-in reward caps at 100',live.ok('GET','/credit/me',token)['score']==100)
    ledger=live.ok('GET','/credit/records',token)['records']
    live.check('Capped reward records actual +1',any(r['eventType']=='CHECKIN' and r['changeAmount']==1 and r['scoreAfter']==100 for r in ledger))
    live.check('Repeated check-in rejected',live.call('PUT',f'/reservations/{rid}/checkin',token)['code']!=200)
    live.check('Checked-in reservation cannot be cancelled',live.call('PUT',f'/reservations/{rid}/cancel',token)['code']!=200)
    tag='CREDIT-EQUIP-'+uuid.uuid4().hex[:6]
    live.ok('POST','/equipments',admin,{'code':tag,'name':'隔离测试设备','labId':lab['id'],'status':'IDLE'})
    asset=live.ok('GET','/equipments?keyword='+tag,admin)['records'][0]
    live.check('ID/code mismatch rejected',live.call('POST','/repairs',token,{'equipmentId':asset['id']+10000,'equipmentCode':tag,'faultDesc':'隔离测试'})['code']!=200)
    def report(_):return live.call('POST','/repairs',token,{'equipmentCode':tag,'faultDesc':'仅按设备编号报修，隔离并发测试','urgency':'NORMAL'})
    with ThreadPoolExecutor(max_workers=2) as pool:responses=list(pool.map(report,range(2)))
    print('Concurrent report results:', [{'code':r.get('code'),'message':r.get('msg')} for r in responses],flush=True)
    live.check('Code-only reports resolve asset; concurrent duplicate blocked',sum(r['code']==200 for r in responses)==1)
    live.check('Report uses actual equipment ID',next(r for r in responses if r['code']==200)['data']['equipmentId']==asset['id'])
    for n in range(3):
        sql(f"INSERT INTO reservation(order_no,user_id,lab_id,start_time,end_time,headcount,purpose,status,checkin_status,create_time) VALUES('{marker}-NS{n}',{uid},{lab['id']},DATE_SUB(NOW(),INTERVAL {n+2} DAY),DATE_SUB(NOW(),INTERVAL {n+1} DAY),1,'隔离近期限制测试','FINISHED','NO_SHOW',NOW())")
    me=live.ok('GET','/credit/me',token)
    live.check('Recent violation ban is independent and shows expiry',me['score']==100 and not me['canReserve'] and me['recentNoShows']==3 and bool(me['restrictedUntil']))
    sql(f"UPDATE reservation SET checkin_status='CHECKED_IN' WHERE user_id={uid} AND order_no LIKE '{marker}-NS%'")
    live.ok('POST','/credit/adjust',admin,{'userId':uid,'targetScore':70,'reason':'隔离测试完成，保留恢复后的演示状态'})
    Path('lab-backend/target/credit-ui-fixture.json').write_text(json.dumps({'username':name,'database':live.DATABASE,'userId':uid}),encoding='utf-8')
    Path('lab-backend/target/credit-live-report.json').write_text(json.dumps({'passed':len(live.passed),'checks':live.passed,'database':live.DATABASE},ensure_ascii=False,indent=2),encoding='utf-8')
    print('COMPLETE:',len(live.passed),'credit/asset checks passed',flush=True)
if __name__=='__main__':main()
