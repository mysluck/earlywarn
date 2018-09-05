package com.jhmk.warn.controller;

import com.alibaba.fastjson.JSONObject;
import com.jhmk.cloudentity.base.BaseEntityController;
import com.jhmk.cloudentity.earlywaring.entity.SmShowLog;
import com.jhmk.cloudentity.earlywaring.entity.UserModel;
import com.jhmk.cloudentity.earlywaring.entity.rule.Rule;
import com.jhmk.cloudentity.earlywaring.entity.rule.Yizhu;
import com.jhmk.cloudservice.warnService.service.RuleService;
import com.jhmk.cloudservice.warnService.webservice.AnalysisXmlService;
import com.jhmk.cloudutil.model.AtResponse;
import com.jhmk.cloudutil.model.ResponseCode;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

/**
 * @author ziyu.zhou
 * @date 2018/7/24 15:46
 */
@Controller
@RequestMapping("/warn/match")
public class RuleMatchController extends BaseEntityController<UserModel> {

    private static final Logger logger = LoggerFactory.getLogger(RuleMatchController.class);


    @Autowired
    RuleService ruleService;
    @Autowired
    AnalysisXmlService analysisXmlService;

    /**
     * 下医嘱规则匹配
     *
     * @param response
     * @param map      一诉五史 诊断 病案首页
     * @throws ExecutionException
     * @throws InterruptedException
     */

    @PostMapping("/ruleMatch")
    @ResponseBody
    public void ruleMatch(HttpServletResponse response, @RequestBody String map) throws ExecutionException, InterruptedException {
        String s2 = ruleService.stringTransform(map);
        JSONObject parse = JSONObject.parseObject(s2);
        Rule fill = Rule.fill(parse);
//        Rule rule = ruleService.anaRule1(paramMap);
        String data = "";
        try {

            data = ruleService.ruleMatchGetResp(fill);
            wirte(response, data);
        } catch (Exception e) {
            logger.info("规则匹配失败:{}" + e.getMessage());
        }
        if (StringUtils.isNotBlank(data)) {
            ruleService.add2LogTable(data, fill);
//            ruleService.add2ShowLog(fill, data);
        }
//        ruleService.getTipList2ShowLog(fill, map);
    }


    /**
     * 下诊断 规则匹配 参数包括（病案首页 诊断 p_id v_id等）
     *
     * @param response
     * @param map
     * @throws ExecutionException
     * @throws InterruptedException
     */
    @PostMapping("/ruleMatchByDiagnose")
    @ResponseBody
    public void ruleMatchByDiagnose(HttpServletResponse response, @RequestBody String map) throws ExecutionException, InterruptedException {
        AtResponse resp = new AtResponse();
        Map<String, String> parse = (Map) JSONObject.parse(map);
        String s = ruleService.anaRule(parse);
        //解析一诉五史
        JSONObject jsonObject = JSONObject.parseObject(s);
        Rule rule = Rule.fill(jsonObject);
        //获取 拼接检验检查报告
        rule = ruleService.getbaogao(rule);
        List<Yizhu> yizhu = ruleService.getYizhu(rule);
        rule.setYizhu(yizhu);
        String data = "";
        try {
            //规则匹配
            data = ruleService.ruleMatchGetResp(rule);
            System.out.println("匹配结果为======================:" + data);
        } catch (Exception e) {
            logger.info("规则匹配失败:{}" + e.getMessage());
        }
        if (StringUtils.isNotBlank(data)) {
            ruleService.add2LogTable(data, rule);
        }
        List<SmShowLog> logList = ruleService.add2ShowLog(rule, data, map);
        resp.setData(logList);
        resp.setResponseCode(ResponseCode.OK);
        //一诉五史信息入库
        wirte(response, resp);
        ruleService.saveRule2Database(rule);

    }

    /**
     * 下医嘱 规则匹配
     *
     * @param response
     * @param map
     * @throws ExecutionException
     * @throws InterruptedException
     */
    @PostMapping("/ruleMatchByDoctorAdvice")
    @ResponseBody
    public void ruleMatchByDoctorAdvice(HttpServletResponse response, @RequestBody String map) throws ExecutionException, InterruptedException {
        AtResponse resp = new AtResponse();
        JSONObject jsonObject = JSONObject.parseObject(map);
        if (Objects.nonNull(jsonObject)) {

            String patient_id = jsonObject.getString("patient_id");
            String visit_id = jsonObject.getString("visit_id");
            Rule rule = ruleService.getRuleFromDatabase(patient_id, visit_id);
            //获取 拼接检验检查报告
            Rule ruleBean = ruleService.getbaogao(rule);
            String data = "";
            try {
                //规则匹配
                data = ruleService.ruleMatchGetResp(ruleBean);
            } catch (Exception e) {
                logger.info("规则匹配失败:{}" + e.getMessage());
            }
            if (StringUtils.isNotBlank(data)) {
                ruleService.add2LogTable(data, ruleBean);
            }
            List<SmShowLog> logList = ruleService.add2ShowLog(rule, data, map);
            resp.setResponseCode(ResponseCode.OK);
            resp.setData(logList);
            wirte(response, resp);
        } else {
            logger.info("医嘱规则匹配传递信息为{}" + map);
        }
    }


}
