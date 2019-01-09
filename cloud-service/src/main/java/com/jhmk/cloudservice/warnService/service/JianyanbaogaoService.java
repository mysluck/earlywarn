package com.jhmk.cloudservice.warnService.service;

import com.alibaba.fastjson.JSONObject;
import com.jhmk.cloudentity.base.BaseController;
import com.jhmk.cloudentity.earlywaring.entity.rule.Jianchabaogao;
import com.jhmk.cloudentity.earlywaring.entity.rule.Jianyanbaogao;
import com.jhmk.cloudentity.earlywaring.entity.rule.Rule;
import com.jhmk.cloudentity.earlywaring.webservice.JianyanbaogaoForAuxiliary;
import com.jhmk.cloudentity.earlywaring.webservice.OriginalJianyanbaogao;
import com.jhmk.cloudservice.warnService.webservice.AnalysisXmlService;
import com.jhmk.cloudservice.warnService.webservice.CdrService;
import com.jhmk.cloudutil.config.BaseConstants;
import com.jhmk.cloudutil.util.DateFormatUtil;
import com.jhmk.cloudutil.util.DbConnectionUtil;
import com.jhmk.cloudutil.util.MapUtil;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.UnsupportedEncodingException;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author ziyu.zhou
 * @date 2018/11/8 14:53
 * 检查报告service
 */

@Component
public class JianyanbaogaoService {
    private static final Logger logger = LoggerFactory.getLogger(JianyanbaogaoService.class);

    @Autowired
    DbConnectionUtil dbConnectionUtil;
    @Autowired
    CdrService cdrService;
    @Autowired
    AnalysisXmlService analysisXmlService;

    public List<Jianyanbaogao> getJianyanbaogaoBypatientIdAndVisitId(String patientId, String visitId) {
        List<Jianyanbaogao> jianyanbaogaoList = new LinkedList<>();
        Connection conn = null;
        CallableStatement cstmt = null;
        ResultSet rs = null;
        try {
            conn = dbConnectionUtil.openGamConnectionDBForBaogao();

//            cstmt = conn.prepareCall(" select * from v_cdss_exam_report");
            cstmt = conn.prepareCall("select * from v_cdss_lab_report WHERE patient_id=? and visit_id=?");
            cstmt.setString(1, patientId);
            cstmt.setString(2, visitId);
            rs = cstmt.executeQuery();// 执行
//            List<Company> companyList = new ArrayList<Company>();
            while (rs.next()) {

                Jianyanbaogao jianyanbaogao = new Jianyanbaogao();
                Optional.ofNullable(rs.getString("lab_item_name")).ifPresent(s -> {
                    try {
                        jianyanbaogao.setLab_item_name(new String(s.getBytes("iso-8859-1"), "GBK"));
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    }
                });
                Optional.ofNullable(rs.getString("report_time")).ifPresent(s -> {
                    try {
                        jianyanbaogao.setReport_time(new String(s.getBytes("iso-8859-1"), "GBK"));
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    }
                });
                Optional.ofNullable(rs.getString("lab_qual_result")).ifPresent(s -> {
                    try {
                        jianyanbaogao.setLab_qual_result(new String(s.getBytes("iso-8859-1"), "GBK"));
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    }
                });
                Optional.ofNullable(rs.getString("result_status_code")).ifPresent(s -> {
                    try {
                        jianyanbaogao.setResult_status_code(new String(s.getBytes("iso-8859-1"), "GBK"));
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    }
                });
                Optional.ofNullable(rs.getString("reference_range")).ifPresent(s -> {
                    try {
                        jianyanbaogao.setReference_range(new String(s.getBytes("iso-8859-1"), "GBK"));
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    }
                });
                Optional.ofNullable(rs.getString("specimen")).ifPresent(s -> {
                    try {
                        jianyanbaogao.setSpecimen(new String(s.getBytes("iso-8859-1"), "GBK"));
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    }
                });
                Optional.ofNullable(rs.getString("lab_result_value")).ifPresent(s -> {
                    try {
                        jianyanbaogao.setLab_result_value(new String(s.getBytes("iso-8859-1"), "GBK"));
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    }
                });
                Optional.ofNullable(rs.getString("lab_result_value_unit")).ifPresent(s -> {
                    try {
                        jianyanbaogao.setLab_result_value_unit(new String(s.getBytes("iso-8859-1"), "GBK"));
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    }
                });
                Optional.ofNullable(rs.getString("lab_sub_item_name")).ifPresent(s -> {
                    try {
                        jianyanbaogao.setLab_sub_item_name(new String(s.getBytes("iso-8859-1"), "GBK"));
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    }
                });
                jianyanbaogaoList.add(jianyanbaogao);
            }
        } catch (SQLException ex2) {
            ex2.printStackTrace();
        } catch (Exception exception) {
            exception.printStackTrace();
        } finally {
            dbConnectionUtil.closeConnectionDB(conn, cstmt, rs);
        }

        //检验报告获取最近时间的
        Map<String, Optional<Jianyanbaogao>> collect = jianyanbaogaoList.stream().collect(Collectors.groupingBy(Jianyanbaogao::getIwantData, Collectors.maxBy((o1, o2) -> DateFormatUtil.parseDateBySdf(o1.getReport_time(), DateFormatUtil.DATETIME_PATTERN_SS).compareTo(DateFormatUtil.parseDateBySdf(o2.getReport_time(), DateFormatUtil.DATETIME_PATTERN_SS)))));
        List<Jianyanbaogao> resultList = new ArrayList<>();
        for (Map.Entry<String, Optional<Jianyanbaogao>> entry : collect.entrySet()) {
            Jianyanbaogao student = entry.getValue().get();
            resultList.add(student);
        }
        return resultList;
    }

    public List<Jianyanbaogao> getJianyanbaogaoFromGyeyCdr(Rule rule) {
        //基础map 放相同数据
        Map<String, String> params = new HashMap<>();
        params.put("oid", BaseConstants.OID);
        /**
         *  广医二院  检查报告
         *  patient_id 是 inp_no
         *  visit_id 是 patient_id
         */
        params.put("patient_id", rule.getInp_no());
        params.put("visit_id", rule.getPatient_id());
        params.put("ws_code", BaseConstants.JHHDRWS006A);
        String jianyanzhubiao = cdrService.getDataByCDR(params, null);
        logger.info("检验主表原始数据为：========={}", jianyanzhubiao);
        //检验数据明细
        params.put("ws_code", BaseConstants.JHHDRWS006B);
        String jybgzbMX = cdrService.getDataByCDR(params, null);
        logger.info("检验明细原始数据为：========={}", jybgzbMX);
        //获取检验报告原始数据
        if (StringUtils.isNotBlank(jybgzbMX)) {
            List<JianyanbaogaoForAuxiliary> jianyanbaogaoForAuxiliaries = analysisXmlService.analysisXml2JianyanbaogaoMX(jybgzbMX);
            List<OriginalJianyanbaogao> originalJianyanbaogaos = analysisXmlService.analysisXml2Jianyanbaogao(jianyanzhubiao, jianyanbaogaoForAuxiliaries);
            rule.setOriginalJianyanbaogaos(originalJianyanbaogaos);
            List<Jianyanbaogao> jianyanbaogaos = analysisXmlService.analysisOriginalJianyanbaogao2Jianyanbaogao(originalJianyanbaogaos);
            logger.info("获取到的检验报告为：{}", JSONObject.toJSONString(jianyanbaogaos));
            return jianyanbaogaos;
        } else {
            return null;
        }
    }


    public List<Jianyanbaogao> getJianyanbaogaoFromCdr(Rule rule) {
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
        //获取入院时间 出院时间
        Map<String, String> hospitalDateMap = analysisXmlService.getHospitalDate(hospitalDate);
        //入院时间
        String admission_time = hospitalDateMap.get("admission_time");
        //出院时间
        String discharge_time = hospitalDateMap.get("discharge_time");
        /**
         * 根据入院出院时间  获取时间段内的检验报告
         */
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
        //检验数据（主表）
        params.put("ws_code", BaseConstants.JHHDRWS006A);
        String jianyanzhubiao = cdrService.getDataByCDR(params, listConditions);
        //检验数据明细
        params.put("ws_code", BaseConstants.JHHDRWS006B);
        String jybgzbMX = cdrService.getDataByCDR(params, listConditions);
        //获取检验报告原始数据
        List<JianyanbaogaoForAuxiliary> jianyanbaogaoForAuxiliaries = analysisXmlService.analysisXml2JianyanbaogaoMX(jybgzbMX);
        List<OriginalJianyanbaogao> originalJianyanbaogaos = analysisXmlService.analysisXml2Jianyanbaogao(jianyanzhubiao, jianyanbaogaoForAuxiliaries);
        List<Jianyanbaogao> jianyanbaogaos = analysisXmlService.analysisOriginalJianyanbaogao2Jianyanbaogao(originalJianyanbaogaos);
        logger.info("获取到的检验报告为：{}", JSONObject.toJSONString(jianyanbaogaos));
        return jianyanbaogaos;
    }

    public static void main(String[] args) {
        JianyanbaogaoService jianyanbaogaoService = new JianyanbaogaoService();
        List<Jianyanbaogao> jianyanbaogaoBypatientIdAndVisitId = jianyanbaogaoService.getJianyanbaogaoBypatientIdAndVisitId("115608460", "2");
        System.out.println(JSONObject.toJSONString(jianyanbaogaoBypatientIdAndVisitId));
    }
}
