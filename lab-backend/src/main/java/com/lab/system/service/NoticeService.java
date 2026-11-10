package com.lab.system.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.system.common.BizException;
import com.lab.system.common.PageResult;
import com.lab.system.entity.Notice;
import com.lab.system.mapper.NoticeMapper;
import com.lab.system.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 通知公告
 */
@Service
@RequiredArgsConstructor
public class NoticeService {

    private final NoticeMapper noticeMapper;

    public PageResult<Notice> page(int pageNum, int pageSize, String keyword) {
        Page<Notice> page = noticeMapper.selectPage(new Page<>(pageNum, pageSize),
                Wrappers.<Notice>lambdaQuery()
                        .like(keyword != null && !keyword.isBlank(), Notice::getTitle, keyword)
                        .orderByDesc(Notice::getCreateTime));
        return PageResult.of(page.getTotal(), page.getRecords());
    }

    public List<Notice> latest(int limit) {
        return noticeMapper.selectList(Wrappers.<Notice>lambdaQuery()
                .eq(Notice::getStatus, 1)
                .orderByDesc(Notice::getCreateTime)
                .last("LIMIT " + limit));
    }

    public void save(Notice notice) {
        if (notice.getTitle() == null || notice.getTitle().isBlank()) {
            throw new BizException("标题不能为空");
        }
        notice.setId(null);
        notice.setPublisherId(UserContext.userId());
        notice.setPublisherName(UserContext.get().getRealName());
        if (notice.getStatus() == null) {
            notice.setStatus(1);
        }
        if (notice.getType() == null) {
            notice.setType("NOTICE");
        }
        noticeMapper.insert(notice);
    }

    public void update(Notice notice) {
        noticeMapper.updateById(notice);
    }

    public void delete(Long id) {
        noticeMapper.deleteById(id);
    }
}
