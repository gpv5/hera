package com.dfire.common.service.impl;

import com.dfire.common.constants.Constants;
import com.dfire.common.entity.HeraAction;
import com.dfire.common.entity.model.TablePageForm;
import com.dfire.common.entity.vo.HeraActionVo;
import com.dfire.common.kv.Tuple;
import com.dfire.common.mapper.HeraJobActionMapper;
import com.dfire.common.service.HeraJobActionService;
import com.dfire.common.service.HeraJobHistoryService;
import com.dfire.common.service.HeraJobService;
import com.dfire.common.util.ActionUtil;
import com.dfire.common.util.BeanConvertUtils;
import com.dfire.common.vo.GroupTaskVo;
import com.dfire.common.vo.JobStatus;
import com.dfire.logs.ScheduleLog;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * @author: <a href="mailto:lingxiao@2dfire.com">凌霄</a>
 * @time: Created in 下午3:43 2018/5/16
 * @desc
 */
@Service("heraJobActionService")
public class HeraJobActionServiceImpl implements HeraJobActionService {

    @Autowired
    private HeraJobActionMapper heraJobActionMapper;

    @Autowired
    @Qualifier("heraJobMemoryService")
    private HeraJobService heraJobService;

    @Autowired
    private HeraJobHistoryService heraJobHistoryService;


    @Override
    public List<HeraAction> batchInsert(List<HeraAction> heraActionList, Long nowAction) {
        ScheduleLog.info("batchInsert-> batch size is :{}", heraActionList.size());
        List<HeraAction> insertList = new ArrayList<>();
        for (HeraAction heraAction : heraActionList) {
            //更新时单条更新
            //原因：批量更新时，在极端情况下，数据会有批量数据处理时间的buff
            //     此时如果有其它地方修改了某条数据 会数据库中的数据被批量更新的覆盖
            if (isNeedUpdateAction(heraAction, nowAction)) {
                update(heraAction);
            } else {
                insertList.add(heraAction);
            }
        }
        if (insertList.size() != 0) {
            heraJobActionMapper.batchInsert(insertList);
        }
        return heraActionList;
    }

    /**
     * 判断是更新该是修改
     *
     * @param heraAction
     * @return
     */
    private boolean isNeedUpdateAction(HeraAction heraAction, Long nowAction) {
        HeraAction action = heraJobActionMapper.findById(heraAction);
        if (action != null) {
            //如果该任务不是在运行中
            if (!Constants.STATUS_RUNNING.equals(action.getStatus())) {
                heraAction.setStatus(action.getStatus());
                heraAction.setHistoryId(action.getHistoryId());
                heraAction.setReadyDependency(action.getReadyDependency());
                heraAction.setGmtCreate(action.getGmtCreate());
            } else {
                BeanUtils.copyProperties(action, heraAction);
                heraAction.setGmtModified(new Date());
            }
            return true;
        } else {
            if (heraAction.getId() < nowAction) {
                heraAction.setStatus(Constants.STATUS_FAILED);
                heraAction.setLastResult("生成action时，任务过时，直接设置为失败");
            }
        }
        return false;

    }

    @Override
    public int insert(HeraAction heraAction, Long nowAction) {
        if (isNeedUpdateAction(heraAction, nowAction)) {
            return heraJobActionMapper.update(heraAction);
        } else {
            return heraJobActionMapper.insert(heraAction);
        }
    }

    @Override
    public int delete(String id) {
        return heraJobActionMapper.delete(id);
    }

    @Override
    public int update(HeraAction heraAction) {
        return heraJobActionMapper.update(heraAction);
    }

    @Override
    public List<HeraAction> getAll() {
        return heraJobActionMapper.getAll();
    }

    @Override
    public HeraAction findById(String actionId) {
        HeraAction heraAction = HeraAction.builder().id(Long.parseLong(actionId)).build();
        return heraJobActionMapper.findById(heraAction);
    }

    @Override
    public HeraAction findLatestByJobId(String jobId) {
        return heraJobActionMapper.findLatestByJobId(jobId);
    }

    @Override
    public List<HeraAction> findByJobId(String jobId) {
        return heraJobActionMapper.findByJobId(jobId);
    }

    @Override
    public int updateStatus(JobStatus jobStatus) {
        HeraAction heraAction = findById(jobStatus.getActionId());
        heraAction.setGmtModified(new Date());
        HeraAction tmp = BeanConvertUtils.convert(jobStatus);
        heraAction.setStatus(tmp.getStatus());
        heraAction.setReadyDependency(tmp.getReadyDependency());
        heraAction.setHistoryId(jobStatus.getHistoryId());
        return update(heraAction);
    }

    @Override
    public Tuple<HeraActionVo, JobStatus> findHeraActionVo(String actionId) {
        HeraAction heraActionTmp = findById(actionId);
        if (heraActionTmp == null) {
            return null;
        }
        return BeanConvertUtils.convert(heraActionTmp);
    }

    @Override
    public JobStatus findJobStatus(String actionId) {
        Tuple<HeraActionVo, JobStatus> tuple = findHeraActionVo(actionId);
        return tuple.getTarget();
    }

    /**
     * 根据jobId查询版本运行信息，只能是取最新版本信息
     *
     * @param jobId
     * @return
     */
    @Override
    public JobStatus findJobStatusByJobId(String jobId) {
        HeraAction heraAction = findLatestByJobId(jobId);
        return findJobStatus(heraAction.getId().toString());
    }

    @Override
    public Integer updateStatus(HeraAction heraAction) {
        return heraJobActionMapper.updateStatus(heraAction);
    }

    @Override
    public Integer updateStatusAndReadDependency(HeraAction heraAction) {
        return heraJobActionMapper.updateStatusAndReadDependency(heraAction);
    }

    @Override
    public List<HeraAction> getTodayAction() {
        return heraJobActionMapper.selectTodayAction(ActionUtil.getInitActionVersion());
    }

    @Override
    public List<String> getActionVersionByJobId(Long jobId) {
        return heraJobActionMapper.getActionVersionByJobId(jobId);
    }

    @Override
    public List<HeraActionVo> getNotRunScheduleJob() {
        return heraJobActionMapper.getNotRunScheduleJob();
    }

    @Override
    public List<HeraActionVo> getFailedJob() {
        return heraJobActionMapper.getFailedJob();
    }


    @Override
    public List<GroupTaskVo> findByJobIds(List<Integer> idList, String startDate, String endDate, TablePageForm pageForm, Integer type) {
        if (idList == null || idList.size() == 0) {
            return null;
        }
        Map<String, Object> params = new HashMap<>(3);

        params.put("startDate", startDate);
        params.put("endDate", endDate);
        params.put("list", idList);
        params.put("page", (pageForm.getPage() - 1) * pageForm.getLimit());
        params.put("limit", pageForm.getPage() * pageForm.getLimit());
        List<HeraAction> actionList;
        if (type == 0) {
            params.put("status", null);
        } else if (type == 1) {
            params.put("status", Constants.STATUS_RUNNING);
        } else if (type == 2) {
            params.put("status", Constants.STATUS_FAILED);
        } else {
            return null;
        }
        pageForm.setCount(heraJobActionMapper.findByJobIdsCount(params));
        actionList = heraJobActionMapper.findByJobIdsAndPage(params);
        List<GroupTaskVo> res = new ArrayList<>(actionList.size());
        actionList.forEach(action -> {
            GroupTaskVo taskVo = new GroupTaskVo();
            taskVo.setActionId(buildFont(String.valueOf(action.getId()), Constants.STATUS_NONE));
            taskVo.setJobId(buildFont(String.valueOf(action.getJobId()), Constants.STATUS_NONE));
            taskVo.setName(buildFont(action.getName(), Constants.STATUS_NONE));

            if (action.getStatus() != null) {
                taskVo.setStatus(buildFont(action.getStatus(), action.getStatus()));
            } else {
                taskVo.setStatus(buildFont("未执行", Constants.STATUS_FAILED));
            }
            taskVo.setLastResult(buildFont(action.getLastResult(), action.getLastResult()));
            if (action.getScheduleType() == 0) {
                taskVo.setReadyStatus(buildFont("独立任务", Constants.STATUS_NONE));
            } else {
                String[] dependencies = action.getDependencies().split(Constants.COMMA);
                StringBuilder builder = new StringBuilder();
                HeraAction heraAction;
                for (String dependency : dependencies) {
                    heraAction = this.findById(dependency);
                    if (heraAction != null) {
                        if (Constants.STATUS_SUCCESS.equals(heraAction.getStatus())) {
                            builder.append(Constants.HTML_FONT_GREEN_LEFT).append("依赖任务:").append(dependency).append(",结束时间:").append(ActionUtil.getFormatterDate(ActionUtil.MON_MIN, heraAction.getStatisticEndTime()));
                        } else if (Constants.STATUS_RUNNING.equals(heraAction.getStatus())) {
                            builder.append(Constants.HTML_FONT_BLUE_LEFT).append("依赖任务:").append(dependency).append(",执行中");
                        } else if (Constants.STATUS_FAILED.equals(heraAction.getStatus())) {
                            builder.append(Constants.HTML_FONT_RED_LEFT).append("依赖任务:").append(dependency).append(",执行失败");
                        } else {
                            builder.append(Constants.HTML_FONT_RED_LEFT).append("依赖任务:").append(dependency).append(",未执行");
                        }
                    } else {
                        builder.append(Constants.HTML_FONT_RED_LEFT).append("依赖任务:").append(dependency).append(",未找到");
                    }
                    builder.append(Constants.HTML_FONT_RIGHT).append(Constants.HTML_NEW_LINE);
                }
                taskVo.setReadyStatus(builder.toString());
            }
            res.add(taskVo);
        });
        return res;
    }

    private String buildFont(String str, String type) {
        if (type == null) {
            return Constants.HTML_FONT_RED_LEFT + str + Constants.HTML_FONT_RIGHT;
        }
        switch (type) {
            case Constants.STATUS_RUNNING:
                return Constants.HTML_FONT_BLUE_LEFT + str + Constants.HTML_FONT_RIGHT;
            case Constants.STATUS_SUCCESS:
                return Constants.HTML_FONT_GREEN_LEFT + str + Constants.HTML_FONT_RIGHT;
            case Constants.STATUS_NONE:
                return Constants.HTML_FONT_LEFT + str + Constants.HTML_FONT_RIGHT;
            case Constants.STATUS_FAILED:
                return Constants.HTML_FONT_RED_LEFT + str + Constants.HTML_FONT_RIGHT;
            default:
                return Constants.HTML_FONT_RED_LEFT + str + Constants.HTML_FONT_RIGHT;
        }
    }
}
