package com.jhmk.cloudservice.warnService.service;


import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import com.jhmk.cloudentity.earlywaring.entity.DocumentMapping;
import com.jhmk.cloudentity.earlywaring.entity.SmHospitalLog;
import com.jhmk.cloudentity.earlywaring.entity.SmShowLog;
import com.jhmk.cloudentity.earlywaring.entity.repository.service.*;
import com.jhmk.cloudentity.earlywaring.entity.rule.*;
import com.jhmk.cloudentity.earlywaring.webservice.JianyanbaogaoForAuxiliary;
import com.jhmk.cloudentity.earlywaring.webservice.OriginalJianyanbaogao;
import com.jhmk.cloudservice.webservice.AnalysisXmlService;
import com.jhmk.cloudservice.webservice.CdrService;
import com.jhmk.cloudutil.config.BaseConstants;
import com.jhmk.cloudutil.config.UrlConstants;
import com.jhmk.cloudutil.config.UrlPropertiesConfig;
import com.jhmk.cloudutil.util.*;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class RuleService {

    @Autowired
    SmOrderRepService smOrderRepService;
    @Autowired
    UserDataModelMappingRepService userDataModelMappingRepService;
    @Autowired
    UserModelRepService userModelRepService;
    @Autowired
    UserModelService userModelService;
    @Autowired
    RestTemplate restTemplate;
    @Autowired
    UrlPropertiesConfig urlPropertiesConfig;
    @Autowired
    HosptailLogService hosptailLogService;
    @Autowired
    SmHospitalLogRepService smHospitalLogRepService;
    @Autowired
    SmShowLogRepService smShowLogRepService;
    @Autowired
    CdrService cdrService;
    @Autowired
    AnalysisXmlService analysisXmlService;
    @Autowired
    BasicInfoRepService basicInfoRepService;
    @Autowired
    BinganshouyeRepService binganshouyeRepService;
    @Autowired
    BinglizhenduanRepService binglizhenduanRepService;
    @Autowired
    ShouyezhenduanRepService shouyezhenduanRepService;
    @Autowired
    BingchengjiluRepService bingchengjiluRepService;
    @Autowired
    RuyuanjiluRepService ruyuanjiluRepService;

    @Autowired
    LogMappingRepService logMappingRepService;
    @Autowired
    BinganshouyeService binganshouyeService;
    @Autowired
    BasicInfoService basicInfoService;
    @Autowired
    ZhenduanService zhenduanService;
    @Autowired
    BingchengjiluService bingchengjiluService;
    @Autowired
    RuyuanjiluService ruyuanjiluService;
    @Autowired
    YizhuRepService yizhuRepService;
    @Autowired
    DocumentMappingRepService documentMappingRepService;
    @Autowired
    ResolveRuleService resolveRuleService;
    @Autowired
    JianchabaogaoService jianchabaogaoService;
    @Autowired
    YizhuService yizhuService;


    private static final Logger logger = LoggerFactory.getLogger(RuleService.class);

    public final String symbol = "[]";
    public final String resultSym = "result";

    /**
     * 去重  获取字段名唯一 日期最大的 map
     *
     * @param formData
     * @param judgeField 去重字段名
     * @param dateField  日期字段名 用来判断时间大小
     * @return
     */
    private List<Map<String, String>> getRecentList(List<Map<String, String>> formData, String judgeField, String dateField) {
        List<Map<String, String>> resultList = new LinkedList<>();
        Map<String, Map<String, String>> onlyMap = new HashMap<>();
        for (Map<String, String> map : formData) {
            //map中存在此类检验、检查报告名  则判断检验日期 取最近
            if (onlyMap.containsKey(map.get(judgeField))) {
                Map<String, String> map1 = onlyMap.get(dateField);
                if ((map1.get(dateField).compareTo(map.get(dateField))) == 1) {
                    onlyMap.put(map.get(judgeField), map);
                }
            } else {
                onlyMap.put(map.get(judgeField), map);
            }
        }

        for (Map.Entry<String, Map<String, String>> entries : onlyMap.entrySet()) {
            resultList.add(entries.getValue());
        }
        return resultList;
    }

    //解析所有原规则（用于界面规则显示）
    public Map<String, Object> formatData(String forObject) {
        Map<String, Object> map = new HashMap<>();
        List<FormatRule> list = new LinkedList<>();
        Map<String, String> recieveData = (Map) JSON.parse(forObject.toString());

        Object recieveOldData = recieveData.get("result");
        Map<String, Object> thisMap = (Map) recieveOldData;
        Object all_page = thisMap.get("all_page");
        map.put("all_page", all_page);
        Object count = thisMap.get("count");
        if (count != null) {
            map.put("count", count);
        }
        Object decisions = thisMap.get("decisions");
        JSONArray oldData = (JSONArray) decisions;
        int size = oldData.size();
        for (int i = 0; i < size; i++) {
            FormatRule formatRule = new FormatRule();
            Map<String, String> obj = (Map) JSON.toJSON(oldData.get(i));
            if (obj.get("ruleCondition") != null) {
                String ruleCondition = obj.get("ruleCondition");
                formatRule = MapUtil.map2Bean(obj, FormatRule.class);
                // 解析"ruleCondition" -> "(门诊病历_主诉_症状名称等于高血压and医嘱_医嘱_医嘱项编码等于aaa)"
//                String condition = disposeRule(ruleCondition);
                String condition = disposeRuleCondition(ruleCondition);
                formatRule.setRuleCondition(condition);
                list.add(formatRule);
            }
        }
        map.put("result", list);
        return map;
    }


    /**
     * 解析规则条件
     *
     * @param ruleCondition
     * @return
     */
    public String disposeRuleCondition(String ruleCondition) {
        String condition = "";
        if (ruleCondition.contains(")and(")) {
            condition = disposeRuleList(ruleCondition);
        } else {
            condition = disposeRule(ruleCondition);
        }
        return condition;
    }

    /**
     * @param ruleCondition (门诊病历_主诉_症状名称等于高血压and医嘱_医嘱_医嘱项编码等于aaa)
     * @return 症状名称等于高血压&医嘱项编码等于aaa
     */
    private String disposeRule(String ruleCondition) {
        //切割 根据and连接符  原始：(门诊病历_主诉_症状名称等于高血压and医嘱_医嘱_医嘱项编码等于aaa)

        String s = ruleCondition.replaceAll("\\(|\\)", "");
        s = s.trim();
        String[] ands = s.split("and");
        StringBuffer sb = new StringBuffer();
        ;
        for (int i = 0; i < ands.length; i++) {
            String condition = ands[i];

            String substring = condition.substring(condition.lastIndexOf("_") + 1);
            if (condition.contains("入院记录_")) {
                sb.append("(入院记录)");
            }
            sb.append(substring);

            if (i != ands.length - 1) {
                sb.append("&");
            }
        }
        return sb.toString();
    }

    private String disposeRuleList(String ruleConditions) {
        //切割 根据and连接符  原始：(门诊病历_主诉_症状名称等于高血压and医嘱_医嘱_医嘱项编码等于aaa)

        String[] ands = ruleConditions.split("\\)and\\(");

        List<String> list = new ArrayList<>();
        for (int i = 0; i < ands.length; i++) {
            String condition = ands[i];
            String s = disposeRule(condition);
            list.add(s);
        }
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < list.size(); i++) {

            sb.append("(").append(list.get(i)).append(")");
            if (i != list.size() - 1) {
                sb.append("and");
            }
        }
        return sb.toString();
    }


    /**
     * 解析现有规则，用于前台重新编辑显示
     *
     * @param data
     * @return
     */
    public List<AnalyzeBean> restoreRule(String data) {
        List<AnalyzeBean> objects = JSON.parseObject(data, new TypeReference<List<AnalyzeBean>>() {
        });
        List<AnalyzeBean> result = new LinkedList<>();
        for (int i = 0, l = objects.size(); i < l; i++) {

            AnalyzeBean analyzeBean = objects.get(i);
            String field = analyzeBean.getField();
            Optional.ofNullable(userModelRepService.findByUmName(field.substring(field.lastIndexOf("_") + 1)).get(0))
                    .ifPresent(s -> {
                        analyzeBean.setType(s.getUmType());
                    });

            //java8
            Optional.ofNullable(userDataModelMappingRepService.findByDmNamePath(field).get(0))
                    .ifPresent(s -> {
                        analyzeBean.setField(s.getUmNamePath());
                    });

            Optional.ofNullable(analyzeBean.getValue()).ifPresent(s -> {
                String value = s.get(0);
                analyzeBean.setValues(value);
            });
            result.add(analyzeBean);
        }
        return result;
    }

    public String getId(String forObject) {
        JSONObject jsonObject = JSONObject.parseObject(forObject);
        String code = jsonObject.getString("code");
        if (BaseConstants.OK.equals(code)) {
            Object result = jsonObject.get("result");
            if (result != null) {
                Map<String, String> map = (Map) result;
                String id = map.get("id");
                return id;
            }
        }
        return "";
    }


    /**
     * 获取规则匹配响应结果
     *
     * @param fill
     * @return
     */
    public String ruleMatchGetResp(Rule fill) {

        String data = "";
        String o = JSONObject.toJSONString(fill);
        String s = StringUtil.stringTransform(o);
        Object parse = JSONObject.parse(s);
        try {
            data = restTemplate.postForObject(urlPropertiesConfig.getCdssurl() + UrlConstants.matchrule, parse, String.class);
            logger.info("匹配规则请求地址uri：{}，请求数据为：{}，结果为{}", urlPropertiesConfig.getCdssurl() + UrlConstants.matchrule, JSONObject.toJSONString(parse), data);
        } catch (Exception e) {
            logger.info("规则匹配失败：,url={},原因:{},请求数据为：{}，返回结果为：{}", urlPropertiesConfig.getCdssurl() + UrlConstants.matchrule, e.getMessage(), JSONObject.toJSONString(parse), data);
        }
        return data;
    }

    /**
     * cdss 规则库匹配
     *
     * @param fill
     * @return
     */
    public String ruleMatchByRuleBase(Rule fill) {
        String data = "";
        String o = JSONObject.toJSONString(fill);
        Object parse = JSONObject.parse(o);
        try {
            data = restTemplate.postForObject(urlPropertiesConfig.getCdssurl() + UrlConstants.matchrule, parse, String.class);
            logger.info("匹配规则请求地址uri：{}，请求数据为：{}，结果为{}", urlPropertiesConfig.getCdssurl() + UrlConstants.matchrule, JSONObject.toJSONString(parse), data);
        } catch (Exception e) {
            logger.info("规则匹配失败：,url={},原因:{},请求数据为：{}，返回结果为：{}", urlPropertiesConfig.getCdssurl() + UrlConstants.matchrule, e.getMessage(), JSONObject.toJSONString(parse), data);
        }
        return data;
    }


    private Rule getSameZhenDuanList(Rule fill) {
        List<Binglizhenduan> binglizhenduan = fill.getBinglizhenduan();
        if (binglizhenduan != null) {
            Set<Binglizhenduan> set = new HashSet<>();
            List<Binglizhenduan> zhenduans = new ArrayList<>();
            for (Binglizhenduan b : binglizhenduan) {
                List<Binglizhenduan> zhenduan = getZhenduan(b);
                set.addAll(zhenduan);
            }
            zhenduans.addAll(set);
            fill.setBinglizhenduan(zhenduans);
        }
        return fill;
    }

    /**
     * 触发规则数据入库sm_hospital_log
     *
     * @param resultData
     * @param mes
     */
    public void add2LogTable(String resultData, Rule mes) {
        String doctor_id = mes.getDoctor_id();
        String patient_id = mes.getPatient_id();
        String visit_id = mes.getVisit_id();
        if (StringUtils.isEmpty(doctor_id)) {
            BasicInfo basicInfo = basicInfoRepService.findByPatient_idAndVisit_id(patient_id, visit_id);
            if (basicInfo != null) {
                doctor_id = basicInfo.getDoctor_id();
            }
        }
        //将提醒状态全部设为3，表示未触发，如果触发，再将状态改为0
        List<SmShowLog> existLogByRuleMatch = smShowLogRepService.findExistLogByRuleMatch(doctor_id, patient_id, visit_id);
        if (existLogByRuleMatch != null && existLogByRuleMatch.size() > 0) {
            smShowLogRepService.updateShowLogStatus(3, doctor_id, patient_id, visit_id, "rulematch", 0);
        }
        JSONObject jsonObject = JSONObject.parseObject(resultData);
        if (!symbol.equals(jsonObject.getString(resultSym))) {
            Object result = jsonObject.get(resultSym);
            SmHospitalLog smHospitalLog = hosptailLogService.addLog(mes);
            if (Objects.nonNull(result)) {
                //todo 预警等级需要返回
                JSONArray ja = (JSONArray) result;
                if (ja.size() > 0) {
                    Iterator<Object> iterator = ja.iterator();
                    while (iterator.hasNext()) {
                        Object next = iterator.next();
                        JSONObject object = JSONObject.parseObject(next.toString());
                        //预警等级
                        String warninglevel = object.getString("warninglevel");
                        smHospitalLog.setAlarmLevel(warninglevel);
                        //释义
                        String hintContent = object.getString("hintContent");
                        String signContent = object.getString("signContent");
                        smHospitalLog.setHintContent(hintContent);
                        smHospitalLog.setSignContent(signContent);
                        smHospitalLog.setRuleSource(object.getString("ruleSource"));
                        smHospitalLog.setClassification(object.getString("classification"));
                        smHospitalLog.setIdentification(object.getString("identification"));
                        smHospitalLog.setNowTime(new Date());
                        String ruleCondition = object.getString("ruleCondition");
                        if (StringUtils.isNotBlank(ruleCondition)) {
                            String condition = disposeRuleCondition(ruleCondition);
                            smHospitalLog.setRuleCondition(condition);
                        }
                        String rule_id = object.getString("_id");
                        //获取触发的规则id
                        smHospitalLog.setRuleId(rule_id);
//                        List<LogMapping> notSaveLogMapping = getNotSaveLogMapping(mes, resultData);
                        JSONArray diseaseMessageMap = object.getJSONArray("diseaseMessageMap");
                        List<LogMapping> notSaveLogMapping = getNotSaveLogMapping(mes, diseaseMessageMap);
                        Collections.sort(notSaveLogMapping, CompareUtil.createComparator(-1, "logTime"));
                        String logTime = notSaveLogMapping.get(0).getLogTime();
                        if (StringUtils.isNotBlank(logTime)) {
                            smHospitalLog.setCreateTime(DateFormatUtil.parseDateBySdf(logTime, DateFormatUtil.DATETIME_PATTERN_SS));
                        } else {
                            smHospitalLog.setCreateTime(new Date());
                        }

                        SmHospitalLog save = smHospitalLogRepService.save(smHospitalLog);
                        //todo 下版本修改 不用主键作为 不要用自增属性字段作为主键与子表关联。不便于系统的迁移和数据恢复。对外统计系统映射关系丢失
                        int id = save.getId();
                        for (LogMapping mapping : notSaveLogMapping) {
                            mapping.setSmHospitalLog(smHospitalLog);
                            logMappingRepService.save(mapping);
                        }
                        if (save == null) {
                            logger.info("入日志库失败:" + save.toString());
                        }


                        SmShowLog log = smShowLogRepService.findFirstByDoctorIdAndPatientIdAndRuleIdAndVisitId(doctor_id, patient_id, rule_id, visit_id);
                        //先将所有规则状态改为3 如果触发规则，则改为0 否则一直为3 表示第二次没有触发此规则，前台自动变灰
                        if (log != null && 3 == log.getRuleStatus()) {
                            int tempId = log.getId();
                            if (logTime != null) {
                                smShowLogRepService.updateSmHospitalById(0, id, logTime, tempId);
                            } else {
                                String date = DateFormatUtil.formatBySdf(new Date(), DateFormatUtil.DATETIME_PATTERN_SS);
                                smShowLogRepService.updateSmHospitalById(0, id, date, tempId);
                            }
                        } else {
                            SmShowLog newLog = new SmShowLog();
                            newLog.setPatientId(patient_id);
                            newLog.setVisitId(visit_id);
                            newLog.setRuleId(rule_id);
                            newLog.setSmHospitalLogId(id);
                            if (logTime != null) {
                                newLog.setDate(logTime);
                            } else {
                                newLog.setDate(DateFormatUtil.formatBySdf(new Date(), DateFormatUtil.DATETIME_PATTERN_SS));
                            }
                            newLog.setDoctorId(doctor_id);
                            newLog.setRuleStatus(0);
                            newLog.setType("ruleMatch");
                            if (StringUtils.isNotBlank(signContent)) {
                                newLog.setHintContent(signContent);
                            } else {
                                newLog.setHintContent(hintContent);
                            }
                            smShowLogRepService.save(newLog);
                        }
                    }
                }
            }
        }

    }

    /**
     * 将触发规则入库
     *
     * @param resultData
     * @param mes
     */
    public void add2LogTableNew(String resultData, Rule mes) {
        JSONObject jsonObject = JSONObject.parseObject(resultData);
        if (!symbol.equals(jsonObject.getString(resultSym))) {
            Object result = jsonObject.get(resultSym);
            SmHospitalLog smHospitalLog = hosptailLogService.addLog(mes);
            if (Objects.nonNull(result)) {
                JSONArray ja = (JSONArray) result;
                if (ja.size() > 0) {
                    Iterator<Object> iterator = ja.iterator();
                    while (iterator.hasNext()) {
                        Object next = iterator.next();
                        JSONObject object = JSONObject.parseObject(next.toString());
                        //预警等级
                        String warninglevel = object.getString("warninglevel");
                        smHospitalLog.setAlarmLevel(warninglevel);
                        //释义
                        String hintContent = object.getString("hintContent");
                        String signContent = object.getString("signContent");
                        smHospitalLog.setHintContent(hintContent);
                        smHospitalLog.setSignContent(signContent);
                        smHospitalLog.setRuleSource(object.getString("ruleSource"));
                        smHospitalLog.setClassification(object.getString("classification"));
                        smHospitalLog.setIdentification(object.getString("identification"));
                        smHospitalLog.setNowTime(new Date());
                        String ruleCondition = object.getString("ruleCondition");
                        if (StringUtils.isNotBlank(ruleCondition)) {
                            String condition = disposeRuleCondition(ruleCondition);
                            smHospitalLog.setRuleCondition(condition);
                        }
                        String rule_id = object.getString("_id");
                        //获取触发的规则id
                        smHospitalLog.setRuleId(rule_id);
                        JSONArray diseaseMessageMap = object.getJSONArray("diseaseMessageMap");
                        List<LogMapping> notSaveLogMapping = getNotSaveLogMapping(mes, diseaseMessageMap);
                        Collections.sort(notSaveLogMapping, CompareUtil.createComparator(-1, "logTime"));
                        String logTime = notSaveLogMapping.get(0).getLogTime();
                        if (StringUtils.isNotBlank(logTime)) {
                            smHospitalLog.setCreateTime(DateFormatUtil.parseDateBySdf(logTime, DateFormatUtil.DATETIME_PATTERN_SS));
                        } else {
                            smHospitalLog.setCreateTime(new Date());
                        }
                        smHospitalLog.setLogMappingList(notSaveLogMapping);

                        SmHospitalLog save = smHospitalLogRepService.save(smHospitalLog);
                        //记录触发的规则入库
                        updateRuleMatchSmShowLog(save);
                    }
                }
            }
        }
    }

    /**
     * 修改规则匹配 小页面 显示
     *
     * @param smHospitalLog
     */
    public void updateRuleMatchSmShowLog(SmHospitalLog smHospitalLog) {
        String doctor_id = smHospitalLog.getDoctorId();
        String patient_id = smHospitalLog.getPatientId();
        String visit_id = smHospitalLog.getVisitId();
        String ruleId = smHospitalLog.getRuleId();
        Date createTime = smHospitalLog.getCreateTime();
        String signContent = smHospitalLog.getSignContent();
        String hintContent = smHospitalLog.getHintContent();
        //将提醒状态全部设为3，表示未触发，如果触发，再将状态改为0
        int id = smHospitalLog.getId();
        SmShowLog log = smShowLogRepService.findFirstByDoctorIdAndPatientIdAndRuleIdAndVisitId(doctor_id, patient_id, ruleId, visit_id);
        //先将所有规则状态改为3 如果触发规则，则改为0 否则一直为3 表示第二次没有触发此规则，前台自动变灰
        if (log != null && 3 == log.getRuleStatus()) {
            int tempId = log.getId();
            if (createTime != null) {
                smShowLogRepService.updateSmHospitalById(0, id, DateFormatUtil.format(createTime, DateFormatUtil.DATETIME_PATTERN_SS), tempId);
            } else {
                String date = DateFormatUtil.formatBySdf(new Date(), DateFormatUtil.DATETIME_PATTERN_SS);
                smShowLogRepService.updateSmHospitalById(0, id, date, tempId);
            }
        } else {
            SmShowLog newLog = new SmShowLog();
            newLog.setPatientId(patient_id);
            newLog.setVisitId(visit_id);
            newLog.setRuleId(ruleId);
            newLog.setSmHospitalLogId(id);
            if (createTime != null) {
                newLog.setDate(DateFormatUtil.format(createTime, DateFormatUtil.DATETIME_PATTERN_SS));
            } else {
                newLog.setDate(DateFormatUtil.formatBySdf(new Date(), DateFormatUtil.DATETIME_PATTERN_SS));
            }
            newLog.setDoctorId(doctor_id);
            newLog.setRuleStatus(0);
            newLog.setType("ruleMatch");
            if (StringUtils.isNotBlank(signContent)) {
                newLog.setHintContent(signContent);
            } else {
                newLog.setHintContent(hintContent);
            }
            smShowLogRepService.save(newLog);
        }
    }

    /**
     * 添加触发规则项到sm_show_log 在查询显示
     *
     * @param data
     * @param fromData
     * @param map
     * @return
     */
    public List<SmShowLog> add2ShowLog(Rule data, String fromData, String map) {
        String doctor_id = data.getDoctor_id();
        String patient_id = data.getPatient_id();
        String visit_id = data.getVisit_id();
        getTipList2ShowLog(data, map);
        List<SmShowLog> byDoctorIdAndPatientId = smShowLogRepService.findByDoctorIdAndPatientIdAndVisitIdOrderByDateDesc(doctor_id, patient_id, visit_id);
        return byDoctorIdAndPatientId;
    }

    public List<SmShowLog> add2ShowLog(Rule data, String map) {
        String doctor_id = data.getDoctor_id();
        String patient_id = data.getPatient_id();
        String visit_id = data.getVisit_id();
        getTipList2ShowLog(data, map);
        List<SmShowLog> byDoctorIdAndPatientId = smShowLogRepService.findByDoctorIdAndPatientIdAndVisitIdOrderByDateDesc(doctor_id, patient_id, visit_id);
        return byDoctorIdAndPatientId;
    }


    /**
     * 将触发规则的数据添加到showlog表中
     *
     * @param data
     * @param fromData
     * @return
     */
    public void addHintRule2ShowLogTable(Rule data, String fromData) {
        String doctor_id = data.getDoctor_id();
        String patient_id = data.getPatient_id();
        String visit_id = data.getVisit_id();
        if (StringUtils.isNotBlank(patient_id) && StringUtils.isNotBlank(doctor_id)) {

            Map<String, Object> parse = (Map) JSON.parse(fromData);
            JSONArray result = (JSONArray) parse.get("result");

            if (result != null && result.size() > 0) {

                JSONArray array = (JSONArray) result;
                Iterator<Object> iterator = array.iterator();
                while (iterator.hasNext()) {
                    JSONObject jsonObject = (JSONObject) iterator.next();
                    String hintContent = jsonObject.getString("hintContent");
                    String rule_id = jsonObject.getString("_id");
                    List<SmShowLog> existLog = smShowLogRepService.findExistLogByRuleMatch(doctor_id, patient_id, visit_id);
                    //todo 可优化 写sql语句 一次完成
                    for (SmShowLog log : existLog) {
                        log.setRuleStatus(3);
                        smShowLogRepService.save(log);
                    }

                    SmShowLog log = smShowLogRepService.findFirstByDoctorIdAndPatientIdAndRuleIdAndVisitId(doctor_id, patient_id, rule_id, visit_id);
                    //先将所有规则状态改为3 如果触发规则，则改为0 否则一直为3 表示第二次没有处罚此规则，前台自动变灰
                    if (log != null && 3 == log.getRuleStatus()) {
                        log.setRuleStatus(0);
                        smShowLogRepService.save(log);
                    } else {
                        SmShowLog newLog = new SmShowLog();
                        newLog.setPatientId(patient_id);
                        newLog.setVisitId(visit_id);
                        newLog.setRuleId(rule_id);
                        SmHospitalLog one = smHospitalLogRepService.findOne(Integer.valueOf(rule_id));
                        Date createTime = one.getCreateTime();
                        newLog.setDate(DateFormatUtil.formatBySdf(createTime, DateFormatUtil.DATETIME_PATTERN_SS));
                        newLog.setDoctorId(doctor_id);
                        newLog.setRuleStatus(0);
                        newLog.setType("ruleMatch");
                        newLog.setHintContent(hintContent);
                        smShowLogRepService.save(newLog);
                    }
                }
            } else {
                List<SmShowLog> existLog = smShowLogRepService.findExistLogByRuleMatch(doctor_id, patient_id, visit_id);
                for (SmShowLog log : existLog) {
                    log.setRuleStatus(3);
                    smShowLogRepService.save(log);
                }
            }
        }
    }

    public List<LogMapping> getNotSaveLogMapping(Rule rule, JSONArray diseaseMessageArray) {
        List<LogMapping> logMappingList = new LinkedList<>();
        List<String> order0List = smOrderRepService.findAllByOrOrderNum(0);
        List<String> order1List = smOrderRepService.findAllByOrOrderNum(1);
        Iterator<Object> iterator1 = diseaseMessageArray.iterator();
        while (iterator1.hasNext()) {
            JSONObject next = (JSONObject) iterator1.next();
            Set<String> keys = next.keySet();
            for (String names : keys) {
                LogMapping logMapping = new LogMapping();
                String name = "";
                //排序第一标志
                int first_order = 0;
                //排序第二标志 排序 检验名=钠 检验值=1 检验名=钾 检验值=2 无法识别字段
                int secord_order = 0;
                //逻辑 ：当返回字段 带@符号是 先根据@后的数字排序  再根据 名 值排序（如先检验项 在检验值）
                if (names.contains("@")) {
                    String[] split = names.split("@");
                    name = split[0];
                    try {
                        first_order = Integer.valueOf(split[1]);
                    } catch (Exception e) {
                        first_order = 1;
                    }
                } else {
                    name = names;
                }
                String chinaName = "";
                DocumentMapping firstByUrlPath = documentMappingRepService.findFirstByUrlPath(name);
                if (Objects.nonNull(firstByUrlPath)) {
                    chinaName = firstByUrlPath.getChinaName();
                } else {
                    chinaName = name;
                }
                if (order0List.contains(name)) {
                    secord_order = 0;
                } else if (order1List.contains(name)) {
                    secord_order = 1;
                }
                String value = next.getString(names);
                String dateByValue = getDateByValue(name, value, rule);
                logMapping.setLogObj(chinaName);
                logMapping.setLogResult(value);
                logMapping.setLogTime(dateByValue);
                logMapping.setLogOrderF(first_order);
                logMapping.setLogOrderS(secord_order);
                logMappingList.add(logMapping);
            }
        }
        return logMappingList;
    }


    /**
     * 根据字段 和 值 获取时间
     *
     * @return
     */
    public String getDateByValue(String key, String value, Rule rule) {
        String time = DateFormatUtil.format(new Date(), DateFormatUtil.DATETIME_PATTERN_SS);

        //检验报告
        //检查报告
        //诊断名称
        //医嘱名称
        //病历名称
        String properties = "";
        String clazz = "";
        if (key.contains(".")) {
            clazz = key.substring(0, key.indexOf("."));
            properties = key.substring(key.lastIndexOf(".") + 1);
        }


        if (clazz.contains("binglizhenduan")) {
            List<Binglizhenduan> binglizhenduanList = rule.getBinglizhenduan();
            for (Binglizhenduan binglizhenduan : binglizhenduanList) {
                Object value1 = ReflexUtil.invokeMethod(properties, binglizhenduan);
                if (value.equals(value1.toString())) {
                    time = binglizhenduan.getDiagnosis_time();
                    return time;
                }

            }
        } else if (clazz.contains("yizhu")) {
            List<Yizhu> yizhu = rule.getYizhu();
            for (Yizhu yz : yizhu) {
                Object value1 = ReflexUtil.invokeMethod(properties, yz);
                if (value.equals(value1.toString())) {
                    time = yz.getOrder_begin_time();
                    return time;
                }

            }
        } else if (clazz.contains("jianchabaogao")) {
            List<Jianchabaogao> jianchabaogao = rule.getJianchabaogao();
            for (Jianchabaogao jc : jianchabaogao) {
                Object value1 = ReflexUtil.invokeMethod(properties, jc);
                if (value.equals(value1.toString())) {
                    time = jc.getExam_time();
                    return time;
                }

            }
        } else if (clazz.contains("jianyanbaogao")) {

            List<Jianyanbaogao> jianyanbaogaoList = rule.getJianyanbaogao();
            for (Jianyanbaogao jc : jianyanbaogaoList) {
                Object value1 = ReflexUtil.invokeMethod(properties, jc);
                if (value.equals(value1.toString())) {
                    time = jc.getReport_time();
                    return time;
                }

            }

            //首页名称
        } else if (clazz.contains("shouyezhenduan")) {
            List<Shouyezhenduan> binglizhenduanList = rule.getShouyezhenduan();
            for (Shouyezhenduan binglizhenduan : binglizhenduanList) {
                Object value1 = ReflexUtil.invokeMethod(properties, binglizhenduan);
                if (value.equals(value1.toString())) {
                    time = binglizhenduan.getDiagnosis_time();
                    return time;
                }

            }
        }
        return time;
    }


    /**
     * 解析规则 一诉五史 key value型转为  键值对：
     *
     * @param paramMap
     * @return
     */
    public String anaRule(Map<String, String> paramMap) {
        Map<String, Object> endparamMap = new HashMap<String, Object>();
        endparamMap.putAll(paramMap);
        for (String key : paramMap.keySet()) {
            if ("ruyuanjilu".equals(key)) {
                String ryjl = String.valueOf(paramMap.get("ruyuanjilu"));
                JSONArray jsonArray = JSON.parseArray(ryjl);
                Iterator<Object> it = jsonArray.iterator();
                Map<String, String> ryjlMap = new HashMap<String, String>();
                while (it.hasNext()) {
                    JSONObject ob = (JSONObject) it.next();
                    String ryjlkey = ob.getString("key");
                    String value = ob.getString("value");
                    if (value != null && !value.isEmpty()) {
                        if ("既往史".equals(ryjlkey)) {
                            ryjlMap.put("history_of_past_illness", value);
                        } else if ("主诉".equals(ryjlkey)) {
                            ryjlMap.put("chief_complaint", value);
                        } else if ("现病史".equals(ryjlkey)) {
                            ryjlMap.put("history_of_present_illness", value);
                        } else if ("家族史".equals(ryjlkey)) {
                            ryjlMap.put("history_of_family_member_diseases", value);
                        } else if ("婚姻史".equals(ryjlkey)) {
                            ryjlMap.put("menstrual_and_obstetrical_histories", value);
                        } else if ("辅助检查".equals(ryjlkey)) {
                            ryjlMap.put("auxiliary_examination", value);
                        } else if ("体格检查".equals(ryjlkey)) {
                            ryjlMap.put("physical_examination", value);
                        }
                    }
                }
                //如果现病史为空 则主诉里第一句话是主诉 剩下的是现病史
                if (StringUtils.isEmpty(ryjlMap.get("history_of_present_illness"))) {
                    String chief_complaint = ryjlMap.get("chief_complaint");
                    if (StringUtils.isNotBlank(chief_complaint)) {
                        String zhusu = chief_complaint.substring(0, chief_complaint.indexOf("。"));
                        String xianbingshi = chief_complaint.substring(chief_complaint.indexOf("。") + 1);
                        ryjlMap.put("chief_complaint", zhusu);
                        ryjlMap.put("history_of_present_illness", xianbingshi);
                    }
                }
                endparamMap.put("ruyuanjilu", ryjlMap);
            } else if ("jianyanbaogao".equals(key)) {
                String jybg = String.valueOf(paramMap.get("jianyanbaogao"));
                JSONArray jsonArray = JSON.parseArray(jybg);
                Iterator<Object> it = jsonArray.iterator();
                List<Map<String, String>> jybgMap = new ArrayList<Map<String, String>>();
                while (it.hasNext()) {
                    Map<String, String> jcbg = new HashMap<String, String>();
                    JSONObject ob = (JSONObject) it.next();
                    if (ob.containsKey("labTestItems")) {
                        Object labTestItems = ob.get("labTestItems");
                        String report_time = ob.getString("report_time");
                        jcbg.put("report_time", report_time);
                        String specimen = ob.getString("specimen");
                        jcbg.put("specimen", specimen);
                        JSONArray sbjsonArray = JSON.parseArray(labTestItems.toString());
                        for (Object object : sbjsonArray) {
                            JSONObject sbobj = (JSONObject) object;
                            if (sbobj.getString("name") != null) {
                                jcbg.put("lab_sub_item_name", sbobj.getString("name"));
                            }
                            if (sbobj.getString("unit") != null) {
                                jcbg.put("lab_result_value_unit", sbobj.getString("unit"));
                            }
                            if (StringUtils.isNotBlank(sbobj.getString("lab_result_value"))) {
                                jcbg.put("lab_result_value", sbobj.getString("lab_result_value"));
                            }
                            if (StringUtils.isNotBlank(sbobj.getString("lab_result"))) {
                                jcbg.put("lab_result_value", sbobj.getString("lab_result"));
                            }
                            if (sbobj.getString("lab_result") != null) {
                                jcbg.put("lab_result_value", sbobj.getString("lab_result"));
                            }
                            if (sbobj.getString("result_status_code") != null) {
                                jcbg.put("result_status_code", sbobj.getString("result_status_code"));
                            }
                            jybgMap.add(jcbg);
                        }
                    }

                }
                endparamMap.put(key, jybgMap);
            }
        }
        return JSONObject.toJSONString(endparamMap);
    }


    public List<Binglizhenduan> getZhenduan(Binglizhenduan b) {
        Set<Binglizhenduan> binglizhenduanSet = new HashSet<>();
        Map<String, String> param = new HashMap<>();
        param.put("diseaseName", b.getDiagnosis_name());
        Object parse1 = JSONObject.toJSON(param);
        String sames = restTemplate.postForObject(urlPropertiesConfig.getCdssurl() + UrlConstants.getSamilarWord, parse1, String.class);
        if (sames != null && !symbol.equals(sames.trim())) {
            JSONArray objects = JSONArray.parseArray(sames);
            Iterator<Object> iterator = objects.iterator();
            while (iterator.hasNext()) {
                Object next = iterator.next();
                Binglizhenduan newBlzd = new Binglizhenduan();
                BeanUtils.copyProperties(b, newBlzd);
                newBlzd.setDiagnosis_name(next.toString());
                binglizhenduanSet.add(newBlzd);
            }
        }
        if (b.getDiagnosis_name() != null && !"".equals(b.getDiagnosis_name().trim())) {
            Binglizhenduan newBlzd = new Binglizhenduan();
            BeanUtils.copyProperties(b, newBlzd);
            binglizhenduanSet.add(newBlzd);
        }
        //发送 获取疾病父类
        String parentList = restTemplate.postForObject(urlPropertiesConfig.getCdssurl() + UrlConstants.getParentList, parse1, String.class);
        if (parentList != null && !symbol.equals(parentList)) {
            JSONArray objects = JSONArray.parseArray(parentList);
            Iterator<Object> iterator = objects.iterator();
            while (iterator.hasNext()) {
                Object next = iterator.next();
                Binglizhenduan newBlzd = new Binglizhenduan();
                BeanUtils.copyProperties(b, newBlzd);
                newBlzd.setDiagnosis_name(next.toString());
                binglizhenduanSet.add(newBlzd);
            }
        }
        List<Binglizhenduan> list = new ArrayList<Binglizhenduan>(binglizhenduanSet);
        return list;
    }

    /**
     * 诊疗提醒状态修改（既往史）
     *
     * @param fill
     * @param map
     */
    public void getTipList2ShowLog(Rule fill, String map) {
        String doctor_id = fill.getDoctor_id();
        String patient_id = fill.getPatient_id();
        String visit_id = fill.getVisit_id();
        if (StringUtils.isNotBlank(doctor_id) && StringUtils.isNotBlank(patient_id)) {
            Object o = JSONObject.parse(map);
            String result = "";
            try {
                result = restTemplate.postForObject(urlPropertiesConfig.getCdssurl() + UrlConstants.getTipList, o, String.class);
                if (StringUtils.isNotBlank(result) && !symbol.equals(result)) {
                    JSONArray array = JSONArray.parseArray(result);
                    Iterator<Object> iterator = array.iterator();
                    while (iterator.hasNext()) {
                        JSONObject next = (JSONObject) iterator.next();
                        String itemName = next.getString("itemName");
                        String type = next.getString("type");
                        String stat = next.getString("stat");
                        List<SmShowLog> logList = smShowLogRepService.findAllByDoctorIdAndPatientIdAndItemNameAndTypeAndStatAndVisitIdOrderByDateDesc(doctor_id, patient_id, itemName, type, stat, visit_id);
                        if (logList != null && logList.size() > 0 && 3 == logList.get(0).getRuleStatus()) {
                            String date = next.getString("data");
                            smShowLogRepService.updateSmHospitalStatusAndDateById(0, date, logList.get(0).getId());
                            continue;
                        } else if (logList == null || logList.size() == 0) {
                            SmShowLog smShowLog = new SmShowLog();
                            smShowLog.setItemName(itemName);
                            smShowLog.setType(type);
                            smShowLog.setStat(stat);
                            String data = next.getString("data");
                            smShowLog.setDate(data);
                            String significance = next.getString("significance");
                            if (StringUtils.isNotBlank(itemName) || StringUtils.isNotBlank(significance) || !"{}".equals(significance.trim())) {

                                smShowLog.setSignificance(significance);
                                String value = next.getString("value");
                                smShowLog.setValue(value);
                                smShowLog.setRuleStatus(0);
                                smShowLog.setPatientId(patient_id);
                                smShowLog.setDoctorId(doctor_id) ;
                                smShowLog.setVisitId(visit_id);
                                smShowLogRepService.save(smShowLog);
                            }
                        }

                    }
                }

            } catch (HttpServerErrorException e) {
                logger.error("访问{}接口出现异常，错误编码:{},错误信息:{}", "getTipList.json", e.getCause(), e.getMessage());
            }
        } else {
            logger.info("医生id或病人id为空,医生id:{},病人id:{}", doctor_id, patient_id);
        }
    }


    /**
     * 获取检验检查报告
     *
     * @param rule
     * @return
     */
    public Rule getbaogao(Rule rule) {
        //检验数据
//        paramMap.put()
        //解析规则 一诉五史 检验报告等

        //基础map 放相同数据
        Map<String, String> baseParams = new HashMap<>();
        baseParams.put("oid", BaseConstants.OID);
        baseParams.put("patient_id", rule.getPatient_id());
        baseParams.put("visit_id", rule.getVisit_id());
        Map<String, String> params = new HashMap<>();
        params.put("ws_code", BaseConstants.JHHDRWS004A);
        params.putAll(baseParams);
        //获取入出转xml
        String hospitalDate = cdrService.getDataByCDR(params, null);
        if (StringUtils.isEmpty(hospitalDate)) {
            return rule;
        }
        //获取入院时间 出院时间
        Map<String, String> hospitalDateMap = analysisXmlService.getHospitalDate(hospitalDate);
        //入院时间
        String admission_time = hospitalDateMap.get("admission_time");
        //出院时间
        String discharge_time = hospitalDateMap.get("discharge_time");

        /**
         * 根据入院出院时间  获取时间段内的检验检查报告
         */
        /**
         *检查数据
         */
        List<Jianchabaogao> jianchabaogaoList = jianchabaogaoService.getJianchabaogaoFromCdr(rule);
        rule.setJianchabaogao(jianchabaogaoList);

        List<Map<String, String>> listConditions = new LinkedList<>();
        if (StringUtils.isNotBlank(admission_time)) {
            Map<String, String> conditionParams = new HashMap<>();
            conditionParams.put("elemName", "REPORT_TIME");
            conditionParams.put("value", admission_time);
//            conditionParams.put("operator", ">=");
            conditionParams.put("operator", "&gt;=");
            listConditions.add(conditionParams);
            rule.setAdmission_time(admission_time);
        }
        if (StringUtils.isNotBlank(discharge_time)) {
            Map<String, String> conditionParams = new HashMap<>();
            conditionParams.put("elemName", "REPORT_TIME");
            conditionParams.put("value", discharge_time);
            conditionParams.put("operator", "&lt;=");
            listConditions.add(conditionParams);
            rule.setDischarge_time(discharge_time);

        }

        //检验数据
//        params.put("ws_code", UrlConstants.JHHDRWS004A);

//        params.put("ws_code", "JHHDRWS006B");
        //检验数据（主表）
        params.put("ws_code", "JHHDRWS006A");
        String jianyanzhubiao = cdrService.getDataByCDR(params, listConditions);
        if (StringUtils.isEmpty(jianyanzhubiao)) {
            return rule;
        }
        //检验数据明细
        params.put("ws_code", "JHHDRWS006B");
        String jybgzbMX = cdrService.getDataByCDR(params, listConditions);
        if (StringUtils.isEmpty(jybgzbMX)) {
            return rule;
        }
        //获取检验报告原始数据
        List<JianyanbaogaoForAuxiliary> jianyanbaogaoForAuxiliaries = analysisXmlService.analysisXml2JianyanbaogaoMX(jybgzbMX);
        List<OriginalJianyanbaogao> originalJianyanbaogaos = analysisXmlService.analysisXml2Jianyanbaogao(jianyanzhubiao, jianyanbaogaoForAuxiliaries);
        rule.setOriginalJianyanbaogaos(originalJianyanbaogaos);
        List<Jianyanbaogao> jianyanbaogaos = analysisXmlService.analysisOriginalJianyanbaogao2Jianyanbaogao(originalJianyanbaogaos);
        rule.setJianyanbaogao(jianyanbaogaos);
        return rule;
    }


    /**
     * 获取医嘱
     *
     * @param rule
     * @return
     */
    /**
     * 添加 更新数据库
     *
     * @param rule
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveRule2Database(Rule rule) {
        try {
            logger.info("当前rule实体类为：{}", JSONObject.toJSONString(rule));
            basicInfoService.saveAndFlush(rule);
            binganshouyeService.saveAndFlush(rule);
            zhenduanService.saveAndFlush(rule);//病例诊断、首页诊断
            ruyuanjiluService.saveAndFlush(rule);
        } catch (Exception e) {
            e.printStackTrace();
            logger.info(e.getMessage());
        }
//        yizhuService.saveAndFlush(rule);
//        bingchengjiluService.saveAndFlush(rule);

    }

    /**
     * 从数据库获取规则信息
     *
     * @return
     */
    public Rule getRuleFromDatabase(String patient_id, String visit_id) {
        Rule rule = new Rule();
        BasicInfo basicInfo = basicInfoRepService.findByPatient_idAndVisit_id(patient_id, visit_id);
        if (basicInfo != null) {
            rule.setPatient_id(patient_id);
            rule.setVisit_id(visit_id);
            rule.setDept_code(basicInfo.getDept_name());
            rule.setDoctor_id(basicInfo.getDoctor_id());
            rule.setPageSource(basicInfo.getPageSource());
            rule.setWarnSource(basicInfo.getWarnSource());
            rule.setDoctor_name(basicInfo.getDept_name());
        }
        rule.setPatient_id(patient_id);
        rule.setVisit_id(visit_id);

        Binganshouye binganshouye = binganshouyeRepService.findByPatient_idAndVisit_id(patient_id, visit_id);
        if (binganshouye != null) {
            rule.setBinganshouye(binganshouye);
        }
        List<Binglizhenduan> binglizhenduanList = binglizhenduanRepService.findAllByPatient_idAndVisit_id(patient_id, visit_id);
        if (binglizhenduanList != null && binglizhenduanList.size() > 0) {
            rule.setBinglizhenduan(binglizhenduanList);
        }
        List<Shouyezhenduan> shouyezhenduanList = shouyezhenduanRepService.findAllByPatient_idAndVisit_id(patient_id, visit_id);
        if (shouyezhenduanList != null && shouyezhenduanList.size() > 0) {
            rule.setShouyezhenduan(shouyezhenduanList);
        }
        List<Yizhu> yizhuList = yizhuRepService.findAllByPatientIdAndVisitId(patient_id, visit_id);
        if (yizhuList != null && yizhuList.size() > 0) {
            rule.setYizhu(yizhuList);
        }
        Bingchengjilu bingchengjilu = bingchengjiluRepService.findByPatient_idAndVisit_id(patient_id, visit_id);
        if (bingchengjilu != null) {
            rule.setBingchengjilu(bingchengjilu);
        }
        return rule;
    }

    /**
     * 下医嘱时获取数据库诊断和一诉五史
     *
     * @param patient_id
     * @param visit_id
     * @return
     */
    public Rule getDiagnoseFromDatabase(String patient_id, String visit_id) {
        Rule rule = new Rule();
        BasicInfo basicInfo = basicInfoRepService.findByPatient_idAndVisit_id(patient_id, visit_id);
        if (basicInfo != null) {
            rule.setPatient_id(patient_id);
            rule.setVisit_id(visit_id);
            rule.setDept_code(basicInfo.getDept_name());
            rule.setDoctor_id(basicInfo.getDoctor_id());
            rule.setPageSource(basicInfo.getPageSource());
            rule.setWarnSource(basicInfo.getWarnSource());
            rule.setDoctor_name(basicInfo.getDept_name());
        } else {
            rule.setPatient_id(patient_id);
            rule.setVisit_id(visit_id);
        }

        Binganshouye binganshouye = binganshouyeRepService.findByPatient_idAndVisit_id(patient_id, visit_id);
        if (binganshouye != null) {
            rule.setBinganshouye(binganshouye);
        }
        List<Binglizhenduan> binglizhenduanList = binglizhenduanRepService.findAllByPatient_idAndVisit_id(patient_id, visit_id);
        if (binglizhenduanList != null && binglizhenduanList.size() > 0) {
            rule.setBinglizhenduan(binglizhenduanList);
        }
        List<Shouyezhenduan> shouyezhenduanList = shouyezhenduanRepService.findAllByPatient_idAndVisit_id(patient_id, visit_id);
        if (shouyezhenduanList != null && shouyezhenduanList.size() > 0) {
            rule.setShouyezhenduan(shouyezhenduanList);
        }
        Bingchengjilu bingchengjilu = bingchengjiluRepService.findByPatient_idAndVisit_id(patient_id, visit_id);
        if (bingchengjilu != null) {
            rule.setBingchengjilu(bingchengjilu);
        }
        return rule;
    }

    /**
     * 根据id获取cdss规则实体类
     *
     * @param id
     * @return
     */
    public FormatRule getFormatRuleById(String id) {
        Map<String, String> idMap = new HashMap<>();
        idMap.put("_id", id);
        Object obj = JSONObject.toJSON(idMap);
        //获取所有子规则id 或者数据
        String result = restTemplate.postForObject(urlPropertiesConfig.getCdssurl() + UrlConstants.getruleforid, obj, String.class);
        JSONObject jsonObject = JSONObject.parseObject(result);
        if (!symbol.equals(result) && StringUtils.isNotBlank(result)) {
            Object resultData = jsonObject.get("result");
            Map<String, String> mapData = (Map) JSONObject.parse(resultData.toString());
            FormatRule formatRule = MapUtil.map2Bean(mapData, FormatRule.class);
            return formatRule;
        } else {
            return null;
        }
    }

    /**
     * 添加标准规则子规则
     *
     * @param condition  规则条件
     * @param formatRule 标准规则
     * @return
     */
    public void addChildRuleByCondition(String condition, FormatRule formatRule) {
        String ruleId = formatRule.getId();
        FormatRule childFormat = new FormatRule();
        BeanUtils.copyProperties(formatRule, childFormat);
        childFormat.setId(null);
        childFormat.setChildElement(null);
        childFormat.setRuleCondition(condition);
        String format = DateFormatUtil.format(new Date(), DateFormatUtil.DATETIME_PATTERN_SS);
        childFormat.setCreateTime(format);
        childFormat.setSubmitDate(format);
        childFormat.setParentId(ruleId);
        childFormat.setIsStandard("false");
        Object o = JSONObject.toJSON(childFormat);
        String forObject = null;
        try {
            forObject = restTemplate.postForObject(urlPropertiesConfig.getCdssurl() + UrlConstants.addrule, o, String.class);
        } catch (Exception e) {
            logger.info("添加子规则失败,原因为：{},返回结果{}", e.getMessage(), forObject);
        } finally {
            JSONObject jsonObject = JSONObject.parseObject(forObject);
            String code = jsonObject.getString("code");
            if (BaseConstants.OK.equals(code)) {
            } else {
                logger.info("添加子规则失败,条件为{},返回结果{}", condition, forObject);
            }
        }

    }

    /**
     * 判断规则条件是否是正确的 ((   1and2and3  ))
     *
     * @param str
     * @return
     */
    public boolean isRule(String str) {
        if ("((".equals(str.substring(1, 2)) && "))".equals(str.substring(str.length() - 2))) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * 将条件修改为标准规则
     *
     * @param str
     * @return
     */
    public String updateOldRule(String str) {
        StringBuffer sb = new StringBuffer("(");
        sb.append(str).append(")");
        return sb.toString();
    }


}
