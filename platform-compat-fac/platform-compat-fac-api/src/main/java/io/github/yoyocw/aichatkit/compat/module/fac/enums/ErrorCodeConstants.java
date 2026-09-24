package io.github.yoyocw.aichatkit.compat.module.fac.enums;
import io.github.yoyocw.aichatkit.compat.framework.common.exception.ErrorCode;


public interface ErrorCodeConstants {


    ErrorCode AERIAL_MISSION_NOT_EXISTS = new ErrorCode(1_501_000_001, "飞防任务不存在");

    // ========== 飞防任务业务逻辑 1_501_014~020 ==========
    ErrorCode MISSION_STATE_NOT_DELETABLE = new ErrorCode(1_501_014_001, "任务不在可删除的状态，不能删除");
    ErrorCode MISSION_GUEST_NO_PERMISSION = new ErrorCode(1_501_015_001, "无权限访问数据");
    ErrorCode MISSION_STATE_CANNOT_SUBMIT = new ErrorCode(1_501_100_002, "当前任务状态{}不能提交");
    ErrorCode MISSION_DISPATCH_NOT_FOUND = new ErrorCode(1_501_100_003, "任务不存在派单数据");
    ErrorCode MISSION_ONLY_DISPATCH_PILOT_ACCEPT = new ErrorCode(1_501_110_001, "只有派单飞手可以接单");
    ErrorCode MISSION_ONLY_ACCEPT_PILOT_CAN_OPERATE = new ErrorCode(1_501_110_002, "非接单人不可进行此操作");
    ErrorCode MISSION_NOT_IN_ACCEPTABLE_STATE = new ErrorCode(1_501_110_002, "当前任务不可接单(仅待接单状态可接单)");
    ErrorCode MISSION_ONLY_PILOT_CAN_ACCEPT = new ErrorCode(1_501_110_004, "只有飞手可以接单");
    ErrorCode ACCEPT_ONLY_OWNER_CAN_MODIFY = new ErrorCode(1_501_110_003, "只能修改自己的接单信息");
    ErrorCode MISSION_ACCEPT_FLOW_ID_REQUIRED = new ErrorCode(1_501_110_005, "接单数据的流程ID不能为空");
    ErrorCode MISSION_RENT_MATERIALS_NOT_AVAILABLE = new ErrorCode(1_501_110_006, "所选择的{}已被其他人预定，需要重新选择后再提交");
    ErrorCode MISSION_ONLY_ADMIN_CAN_APPLY_REFLY = new ErrorCode(1_501_110_007, "只有管理员/飞防管理员可以提出复飞");
    ErrorCode MISSION_REFLY_NOT_ALLOWED_STATE = new ErrorCode(1_501_110_008, "当前状态{}不能提出复飞");
    ErrorCode MISSION_ONLY_ADMIN_CAN_TERMINATE = new ErrorCode(1_501_016_001, "只有管理员可以中止任务");
    ErrorCode MISSION_CANNOT_TERMINATE = new ErrorCode(1_501_016_002, "任务状态{}不能中止，只有未归档(状态<150)的任务可中止");


    ErrorCode MISSION_ACCEPT_NOT_EXISTS = new ErrorCode(1_501_001_001, "接单不存在");

    ErrorCode AERIAL_MISSION_ACCEPTANCE_DATA_NOT_EXISTS = new ErrorCode(1_501_002_001, "飞防作业验收数据不存在");

    ErrorCode AERIAL_MISSION_ACCEPTANCE_SETTLEMENT_NOT_EXISTS = new ErrorCode(1_501_003_001, "验收结算不存在");

    ErrorCode AERIAL_MISSION_ACCEPTANCE_VERIFICATION_NOT_EXISTS = new ErrorCode(1_501_004_001, "验收前现场核查不存在");

    ErrorCode AERIAL_MISSION_ACCEPT_PILOT_NOT_EXISTS = new ErrorCode(1_501_005_001, "接单飞手信息不存在");

    ErrorCode AERIAL_MISSION_AIRSPACE_APPLICATION_NOT_EXISTS = new ErrorCode(1_501_006_001, "空域申请不存在");

    ErrorCode MISSION_DISPATCH_NOT_EXISTS = new ErrorCode(1_501_007_001, "派单不存在");
    ErrorCode MISSION_DISPATCH_PILOT_ID_REQUIRED = new ErrorCode(1_501_100_004, "指派飞手时飞手ID不能为空");

    ErrorCode AERIAL_MISSION_MATERIALS_REQUISITION_NOT_EXISTS = new ErrorCode(1_501_009_001, "物资领用不存在");

    ErrorCode AERIAL_MISSION_MATERIALS_RETURN_NOT_DONE = new ErrorCode(1_501_009_002, "请先保存归还物资信息");

    ErrorCode AERIAL_MISSION_REFLY_NOT_EXISTS = new ErrorCode(1_501_009_003, "补喷记录不存在");

    ErrorCode UAV_STATUS_NOT_AVAILABLE = new ErrorCode(1_501_009_004, "无人机{}当前状态不可选择，请选择空闲状态的无人机");

    ErrorCode AERIAL_MISSION_PLAN_NOT_EXISTS = new ErrorCode(1_501_010_001, "作业方案不存在");

    ErrorCode AERIAL_MISSION_PROGRESS_NOT_EXISTS = new ErrorCode(1_501_011_001, "任务进度不存在");

    ErrorCode AERIAL_MISSION_EXCEPTION_NOT_EXISTS =new ErrorCode(1_501_012_001, "任务异常数据不存在");

    ErrorCode AERIAL_MISSION_FLOW_NOT_EXISTS =new ErrorCode(1_501_013_001, "任务流程数据不存在");

    ErrorCode AERIAL_MISSION_SURVEY_NOT_EXISTS = new ErrorCode(1_501_017_001, "勘察调研数据不存在");

    ErrorCode AERIAL_MISSION_PESTICIDE_NOT_EXISTS = new ErrorCode(1_501_018_001, "药剂方案数据不存在");

    ErrorCode FOREST_FARM_NOT_EXISTS = new ErrorCode(1_502_000_001, "林场不存在");

    ErrorCode FOREST_FARM_USER_NOT_EXISTS = new ErrorCode(1_502_001_001, "林场用户不存在");

    ErrorCode FOREST_FARM_USER_ALREADY_BIND = new ErrorCode(1_502_001_002, "该用户已绑定林场，不允许重复绑定");

    ErrorCode MATERIALS_PESTICIDE_NOT_EXISTS = new ErrorCode(1_503_000_001, "药剂信息不存在");

    ErrorCode MATERIALS_PESTICIDE_INBOUND_NOT_EXISTS = new ErrorCode(1_503_001_001, "药剂入库不存在");

    ErrorCode MATERIALS_PESTICIDE_OUTBOUND_NOT_EXISTS = new ErrorCode(1_503_002_001, "药剂出库不存在");

    ErrorCode MATERIALS_PESTICIDE_OUTBOUND_COUNT_EXCEEDS_INVENTORY = new ErrorCode(1_503_002_002, "药剂出库数量大于库存数量，出库失败");

    ErrorCode MATERIALS_PESTICIDE_RETURN_NOT_EXISTS = new ErrorCode(1_503_003_001, "药剂归还不存在");

    ErrorCode MATERIALS_PESTICIDE_STOCKTAKING_NOT_EXISTS = new ErrorCode(1_503_004_001, "药剂盘点不存在");

    ErrorCode MATERIALS_UAV_NOT_EXISTS = new ErrorCode(1_504_000_001, "无人机不存在");

    // ========== 无人机权限 1_504_000_00x ==========
    ErrorCode UAV_ONLY_MODIFY_OWN = new ErrorCode(1_504_000_004, "只能修改自己的无人机");

    // 无人机删除状态限制：1=预定、2=作业中 状态不可删除
    ErrorCode UAV_NOT_DELETABLE_IN_USE = new ErrorCode(1_504_000_005, "无人机当前为{}状态，不可删除");

    ErrorCode MATERIALS_UAV_MAINTENANCE_NOT_EXISTS = new ErrorCode(1_504_001_001, "无人机维保不存在");

    ErrorCode MATERIALS_UAV_OUTBOUND_NOT_EXISTS = new ErrorCode(1_504_002_001, "无人机出库不存在");

    ErrorCode MATERIALS_UAV_RETURN_NOT_EXISTS = new ErrorCode(1_504_003_001, "无人机归还不存在");

    ErrorCode FLIGHT_RECORD_NOT_EXISTS = new ErrorCode(1_504_004_001, "无人机飞行记录不存在");

    ErrorCode MESSAGE_NOT_EXISTS = new ErrorCode(1_505_000_001, "消息通知不存在");

    // ========== 飞手信息 1_506_000_00x ==========
    ErrorCode PILOT_NOT_EXISTS = new ErrorCode(1_506_000_001, "飞手信息不存在");
    ErrorCode PILOT_USER_NOT_MATCH = new ErrorCode(1_506_000_002, "飞手信息与用户不匹配");
    ErrorCode PILOT_USER_ALREADY_EXISTS = new ErrorCode(1_506_000_004, "该用户已注册为飞手");
    ErrorCode PILOT_CANNOT_MODIFY_OTHERS = new ErrorCode(1_506_000_005, "只能修改自己的信息");
    ErrorCode PILOT_ONLY_ADMIN_CAN_DELETE = new ErrorCode(1_506_000_006, "仅管理员可删除");
    ErrorCode PILOT_ONLY_ADMIN_CAN_BATCH_DELETE = new ErrorCode(1_506_000_007, "仅管理员可批量删除");
    ErrorCode PILOT_ONLY_ADMIN_CAN_REVIEW = new ErrorCode(1_506_000_008, "仅管理员可审核");
    ErrorCode PILOT_INFO_NOT_OBTAINED = new ErrorCode(1_506_000_009, "未能获取到飞手信息，请检查登录用户情况");

    // ========== 飞手状态相关 1_506_001_00x ==========
    ErrorCode PILOT_PENDING_REVIEW = new ErrorCode(1_006_001_000, "飞手待审核，暂无法执行此操作");
    ErrorCode PILOT_DISABLED = new ErrorCode(1_006_002_000, "飞手账号已禁用，无法执行此操作");
    ErrorCode PILOT_STATUS_INVALID = new ErrorCode(1_006_003_000, "飞手状态异常，请联系管理员");

    ErrorCode MISSION_PILOT_NOT_EXISTS = new ErrorCode(1_506_000_003, "任务飞手关联不存在");

    // ========== 飞手评价 1_507_000_00x ==========
    ErrorCode PILOT_EVALUATION_NOT_EXISTS = new ErrorCode(1_507_001_001, "飞手评价不存在");

    // ========== 代码生成补齐：生成 CRUD 时遗漏的 NOT_EXISTS 错误码（1_508_xxx） ==========
    ErrorCode USER_EXPERT_NOT_EXISTS = new ErrorCode(1_508_001_001, "专家不存在");
    ErrorCode USER_EXPERT_USER_ALREADY_EXISTS = new ErrorCode(1_508_001_002, "该用户已注册为专家");
    ErrorCode USER_EXPERT_CANNOT_MODIFY_OTHERS = new ErrorCode(1_508_001_003, "只能修改自己的信息");
    ErrorCode USER_EXPERT_ONLY_ADMIN_CAN_DELETE = new ErrorCode(1_508_001_004, "仅管理员可删除");
    ErrorCode USER_EXPERT_ONLY_ADMIN_CAN_BATCH_DELETE = new ErrorCode(1_508_001_005, "仅管理员可批量删除");
    ErrorCode USER_EXPERT_ONLY_ADMIN_CAN_REVIEW = new ErrorCode(1_508_001_006, "仅管理员可审核");
    // ========== 专家状态相关（verify_status：0待审核/1通过/2禁用/3驳回） ==========
    ErrorCode EXPERT_DISABLED = new ErrorCode(1_508_001_007, "专家账号已禁用，无法执行此操作");
    ErrorCode EXPERT_PENDING_REVIEW = new ErrorCode(1_508_001_008, "专家待审核，暂无法执行此操作");
    ErrorCode EXPERT_STATUS_INVALID = new ErrorCode(1_508_001_009, "专家状态异常，请联系管理员");
    ErrorCode USER_FOREST_NOT_EXISTS = new ErrorCode(1_508_002_001, "用户林场不存在");
    ErrorCode USER_FOREST_USER_ALREADY_EXISTS = new ErrorCode(1_508_002_002, "该用户已注册为林场用户");
    ErrorCode USER_FOREST_CANNOT_MODIFY_OTHERS = new ErrorCode(1_508_002_003, "只能修改自己的信息");
    ErrorCode USER_FOREST_ONLY_ADMIN_CAN_DELETE = new ErrorCode(1_508_002_004, "仅管理员可删除");
    ErrorCode USER_FOREST_ONLY_ADMIN_CAN_BATCH_DELETE = new ErrorCode(1_508_002_005, "仅管理员可批量删除");
    ErrorCode USER_FOREST_ONLY_ADMIN_CAN_REVIEW = new ErrorCode(1_508_002_006, "仅管理员可审核");
    // ========== 林场用户状态相关（verify_status：0待审核/1通过/2禁用/3驳回） ==========
    ErrorCode FOREST_DISABLED = new ErrorCode(1_508_002_007, "林场用户账号已禁用，无法执行此操作");
    ErrorCode FOREST_PENDING_REVIEW = new ErrorCode(1_508_002_008, "林场用户待审核，暂无法执行此操作");
    ErrorCode FOREST_STATUS_INVALID = new ErrorCode(1_508_002_009, "林场用户状态异常，请联系管理员");
    ErrorCode USER_PILOT_NOT_EXISTS = new ErrorCode(1_508_003_001, "用户飞手不存在");
    ErrorCode USER_PILOT_USER_ALREADY_EXISTS = new ErrorCode(1_508_003_002, "该用户已注册为飞手");
    ErrorCode USER_PILOT_CANNOT_MODIFY_OTHERS = new ErrorCode(1_508_003_003, "只能修改自己的信息");
    ErrorCode USER_PILOT_ONLY_ADMIN_CAN_DELETE = new ErrorCode(1_508_003_004, "仅管理员可删除");
    ErrorCode USER_PILOT_ONLY_ADMIN_CAN_BATCH_DELETE = new ErrorCode(1_508_003_005, "仅管理员可批量删除");
    ErrorCode USER_PILOT_ONLY_ADMIN_CAN_REVIEW = new ErrorCode(1_508_003_006, "仅管理员可审核");
    ErrorCode AERIAL_MISSION_FOREST_INFO_NOT_EXISTS = new ErrorCode(1_508_004_001, "飞防任务林区信息不存在");
    ErrorCode CHECK_RECORD_NOT_EXISTS = new ErrorCode(1_508_005_001, "核查记录不存在");
    ErrorCode FLOW_NOT_EXISTS = new ErrorCode(1_508_006_001, "流程不存在");
    ErrorCode FLOW_EVENT_NOT_EXISTS = new ErrorCode(1_508_007_001, "流程事件不存在");
    ErrorCode FLOW_RECORD_NOT_EXISTS = new ErrorCode(1_508_008_001, "流程记录不存在");
    ErrorCode AERIAL_MISSION_DISPATCH_DATA_NOT_EXISTS = new ErrorCode(1_508_009_001, "飞防派单业务数据不存在");
    ErrorCode AERIAL_MISSION_ACCEPT_DATA_NOT_EXISTS = new ErrorCode(1_508_010_001, "飞防接单业务数据不存在");
    ErrorCode MATERIALS_CAR_NOT_EXISTS = new ErrorCode(1_508_011_001, "车辆不存在");
    ErrorCode MATERIALS_CAR_STATUS_NOT_DELETABLE = new ErrorCode(1_508_011_002, "车辆状态为预定或作业中，不能删除");
    ErrorCode MATERIALS_CAR_OUTBOUND_NOT_EXISTS = new ErrorCode(1_508_012_001, "车辆出库不存在");
    ErrorCode MATERIALS_CAR_RETURN_NOT_EXISTS = new ErrorCode(1_508_013_001, "车辆归还不存在");
    ErrorCode FLOW_CHECK_NOT_EXISTS = new ErrorCode(1_508_014_001, "飞防业务审核不存在");

    /** 流程不在可删除状态（仅草稿 1000 / 待平台派单 1010 允许删除） */
    ErrorCode FLOW_NOT_DELETABLE = new ErrorCode(1_508_006_002, "流程不在可删除状态（仅草稿/待平台派单可删除）");

    /** 流程不在可修改状态（仅草稿 1000 允许修改任务信息） */
    ErrorCode FLOW_NOT_EDITABLE = new ErrorCode(1_508_006_003, "流程不在草稿状态1000，不能修改任务信息");

    // ========== 测绘业务 1_509_xxx ==========
    ErrorCode SURVEY_MISSION_NOT_EXISTS = new ErrorCode(1_509_001_001, "测绘任务不存在");
    ErrorCode SURVEY_MISSION_FOREST_INFO_NOT_EXISTS = new ErrorCode(1_509_002_001, "测绘林场信息不存在");
    ErrorCode SURVEY_MISSION_PLAN_NOT_EXISTS = new ErrorCode(1_509_003_001, "测绘方案不存在");
    ErrorCode SURVEY_MISSION_CONFIDENTIALITY_NOT_EXISTS = new ErrorCode(1_509_004_001, "保密协议不存在");
    ErrorCode SURVEY_MISSION_EQUIPMENT_DISPATCH_NOT_EXISTS = new ErrorCode(1_509_005_001, "设备调配不存在");
    ErrorCode SURVEY_MISSION_OPERATION_NOT_EXISTS = new ErrorCode(1_509_006_001, "作业执行记录不存在");
    ErrorCode SURVEY_MISSION_ACCEPTANCE_DATA_NOT_EXISTS = new ErrorCode(1_509_007_001, "验收资料不存在");
    ErrorCode SURVEY_MISSION_SETTLEMENT_NOT_EXISTS = new ErrorCode(1_509_008_001, "结算不存在");
    ErrorCode SURVEY_MISSION_REFLY_NOT_EXISTS = new ErrorCode(1_509_009_001, "复飞审核记录不存在");

    ErrorCode SURVEY_FLOW_NOT_EXISTS = new ErrorCode(1_509_010_001, "测绘流程不存在");
    ErrorCode SURVEY_FLOW_NOT_EDITABLE = new ErrorCode(1_509_010_002, "测绘流程不在草稿状态，不能修改任务信息");
    ErrorCode SURVEY_FLOW_NOT_DELETABLE = new ErrorCode(1_509_010_003, "测绘流程不在草稿状态，不能删除");
    ErrorCode SURVEY_FLOW_NO_PERMISSION = new ErrorCode(1_509_010_004, "无权访问该测绘流程");




}
