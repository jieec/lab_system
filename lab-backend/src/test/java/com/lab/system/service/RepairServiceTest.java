package com.lab.system.service;
import com.lab.system.common.BizException;
import com.lab.system.dto.RepairCreateDTO;
import com.lab.system.entity.*;
import com.lab.system.mapper.*;
import com.lab.system.security.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class RepairServiceTest {
    RepairOrderMapper orders=mock(RepairOrderMapper.class);RepairLogMapper logs=mock(RepairLogMapper.class);
    EquipmentMapper equipment=mock(EquipmentMapper.class);LabMapper labs=mock(LabMapper.class);SysUserMapper users=mock(SysUserMapper.class);
    MessageService messages=mock(MessageService.class);
    RepairService service=new RepairService(orders,logs,equipment,labs,users,messages);
    Equipment asset;
    @BeforeEach void setup(){
        LoginUser user=new LoginUser();user.setId(7L);UserContext.set(user);
        asset=new Equipment();asset.setId(9L);asset.setCode("EQ-009");asset.setName("显微镜");asset.setLabId(1L);asset.setStatus("IDLE");
        when(equipment.selectOne(any())).thenReturn(asset);when(equipment.selectByIdForUpdate(9L)).thenReturn(asset);
        when(orders.selectCount(any())).thenReturn(0L);
    }
    @AfterEach void clean(){UserContext.clear();}
    RepairCreateDTO dto(){RepairCreateDTO d=new RepairCreateDTO();d.setEquipmentCode("EQ-009");d.setFaultDesc("灯不亮");return d;}
    @Test void codeOnlyResolvesActualAssetAndLocksIt(){
        RepairOrder result=service.create(dto());assertEquals(9L,result.getEquipmentId());verify(equipment).selectByIdForUpdate(9L);
    }
    @Test void inconsistentCodeAndIdAreRejected(){
        RepairCreateDTO d=dto();d.setEquipmentId(10L);
        assertThrows(BizException.class,()->service.create(d));verify(orders,never()).insert(any(RepairOrder.class));
    }
    @Test void missingAssetIdentifierIsRejected(){
        assertThrows(BizException.class,()->service.create(new RepairCreateDTO()));
    }
    @Test void existingActiveOrderBlocksDuplicateReport(){
        when(orders.selectCount(any())).thenReturn(1L);
        assertThrows(BizException.class,()->service.create(dto()));verify(orders,never()).insert(any(RepairOrder.class));
    }
    @Test void scrappedAssetIsRejected(){asset.setStatus("SCRAPPED");assertThrows(BizException.class,()->service.create(dto()));}
    @Test void lockedRepairStatusBlocksStaleSnapshotDuplicate(){asset.setStatus("REPAIR");assertThrows(BizException.class,()->service.create(dto()));verify(orders,never()).insert(any(RepairOrder.class));}
}
