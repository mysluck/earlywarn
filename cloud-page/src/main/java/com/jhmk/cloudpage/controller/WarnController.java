package com.jhmk.cloudpage.controller;

import com.alibaba.fastjson.JSONObject;
import com.jhmk.cloudentity.base.BaseController;
import com.jhmk.cloudentity.common.JiaheRuleBean;
import com.jhmk.cloudentity.earlywaring.entity.SmShowLog;
import com.jhmk.cloudentity.earlywaring.entity.repository.service.SmShowLogRepService;
import com.jhmk.cloudentity.earlywaring.entity.rule.Rule;
import com.jhmk.cloudentity.earlywaring.entity.rule.Ruyuanjilu;
import com.jhmk.cloudpage.config.SocketPropConf;
import com.jhmk.cloudservice.cdssPageService.WarnService;
import com.jhmk.cloudservice.warnService.service.RuleService;
import com.jhmk.cloudutil.model.AtResponse;
import com.jhmk.cloudutil.model.ResponseCode;
import com.jhmk.cloudutil.util.SocketUtil;
import com.jhmk.cloudutil.util.StringUtil;
import io.swagger.annotations.*;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.servlet.http.HttpServletResponse;
import java.util.*;

/**
 * @author ziyu.zhou
 * @date 2019/1/11 13:38
 * 提醒控制层
 */

@Controller
@RequestMapping("/warn")
@Api(description = "预警提醒页面功能列表", value = "预警提醒控制层")
public class WarnController extends BaseController {
    @Value("${hospital}")
    private String hospital;
    Logger logger = LoggerFactory.getLogger(WarnController.class);
    @Autowired
    WarnService warnService;
    @Autowired
    SmShowLogRepService smShowLogRepService;
    @Autowired
    SocketPropConf socketPropConf;
    @Autowired
    RuleService ruleService;

    @ApiOperation(value = "规则提醒", notes = "请求数据:    @ApiOperation(value = \"规则提醒\", notes = \"请求数据:(病历信息，如入院记录，病历诊断等)\",\n",
            httpMethod = "POST", responseContainer = "Map")
    @ApiResponses({@ApiResponse(code = 200, message = "成功")})
    @ApiImplicitParams({
            @ApiImplicitParam(name = "map", value = "请求参数", required = true, paramType = "body")
    })
    @PostMapping("/ruleMatch")
    public void ruleMatch(HttpServletResponse response, @RequestBody String map) {
        AtResponse resp = new AtResponse(System.currentTimeMillis());
        System.out.println(hospital);
        //1. 拼接规则  基本信息 检验检查 医嘱
        Rule rule = warnService.getRule(map, hospital);
        //2. 规则匹配
        logger.info("下诊断规则匹配json串：{}", JSONObject.toJSONString(rule));
        String data = ruleService.ruleMatchByRuleBase(rule);
        // 2.1 修改页面显示触发项的状态为  由 0（未修改的原始状态） 到 3（自动置灰的状态）
        smShowLogRepService.updateShowLogStatus(3, rule.getDoctor_id(), rule.getPatient_id(), rule.getVisit_id(), 0);
        // 2.2 将匹配结果入库
        ruleService.add2LogTableNew(data, rule);
        //3. 既往史匹配
        //3.1 匹配结果入库
        List<SmShowLog> logList = ruleService.add2ShowLog(rule, map);
        resp.setData(logList);
        resp.setResponseCode(ResponseCode.OK);
        //4. 返回信息
        wirte(response, resp);
    }

    /**
     * 检验报告解读
     *
     * @param response
     * @param map
     */

    @ApiOperation(value = "获取检验报告解读", notes = "注意参数:(patientId，visitId，doctorId)，从数据库查此病历触发的检验报告lab项",
            httpMethod = "POST", responseContainer = "Map")
    @ApiResponses({@ApiResponse(code = 200, message = "成功")})
    @ApiImplicitParams({
            @ApiImplicitParam(name = "map", value = "请求参数", required = true, paramType = "body")
    })
    @PostMapping("/interpretLab")
    public void interpretLab(HttpServletResponse response, @RequestBody String map) {
        AtResponse resp = new AtResponse(System.currentTimeMillis());
        JSONObject object = JSONObject.parseObject(map);
        String patientId = object.getString("patientId");
        String visitId = object.getString("visitId");
        String doctorId = object.getString("doctorId");
        List<SmShowLog> lab = smShowLogRepService.findAllByDoctorIdAndPatientIdAndVisitIdAndType(doctorId, patientId, visitId, "lab");
        resp.setData(lab);
        resp.setResponseCode(ResponseCode.OK);
        wirte(response, resp);
    }

    @PostMapping("/ruleMatchNew")
    public void ruleMatchNew(HttpServletResponse response, @RequestBody String map) {
        AtResponse resp = new AtResponse(System.currentTimeMillis());
        System.out.println(hospital);
        //1. 拼接规则  基本信息 检验检查 医嘱
        Rule rule = warnService.getRule(map, hospital);
        //2. 规则匹配
        logger.info("下诊断规则匹配json串：{}", JSONObject.toJSONString(rule));
        String data = ruleService.ruleMatchByRuleBase(rule);
        // 2.1 修改页面显示触发项的状态为  由 0（未修改的原始状态） 到 3（自动置灰的状态）
        smShowLogRepService.updateShowLogStatus(3, rule.getDoctor_id(), rule.getPatient_id(), rule.getVisit_id(), 0);
        // 2.2 将匹配结果入库
        ruleService.add2LogTableNew(data, rule);
        //3. 既往史匹配
        //3.1 匹配结果入库
        List<SmShowLog> logList = ruleService.add2ShowLog(rule, map);
        resp.setData(logList);
        resp.setResponseCode(ResponseCode.OK);
        //4. 返回信息
        wirte(response, resp);
    }

    @ApiOperation(value = "解析emr传参,规则匹配所需", notes = "将html解析为可用数据",
            httpMethod = "POST", responseContainer = "Map")
    @ApiResponses({@ApiResponse(code = 200, message = "成功")})
    @ApiImplicitParams({
            @ApiImplicitParam(name = "map", value = "请求参数", required = true, paramType = "body")
    })
    @PostMapping("/analyzeHtml")
    public void analyzeHtml(HttpServletResponse response, @RequestBody String map) {
        AtResponse resp = new AtResponse(System.currentTimeMillis());
        JiaheRuleBean jiaheRuleBean = warnService.analyzeEmrData2Jiaherulebean(map, null, socketPropConf.getIp(), socketPropConf.getPort2());
        resp.setData(jiaheRuleBean);
        resp.setResponseCode(ResponseCode.OK);
        wirte(response, resp);
    }

    public void analyzeHtml2JiaheRuleBean(HttpServletResponse response, @RequestBody String map) {
        AtResponse resp = new AtResponse(System.currentTimeMillis());
        JiaheRuleBean jiaheRuleBean = warnService.analyzeEmrData2Jiaherulebean(map, null, socketPropConf.getIp(), socketPropConf.getPort2());
        resp.setData(jiaheRuleBean);
        resp.setResponseCode(ResponseCode.OK);
        wirte(response, resp);
    }

    @ApiOperation(value = "解析emr传参，常见病，罕见病等数据结构", notes = "将html解析为可用数据",
            httpMethod = "POST", responseContainer = "Map")
    @ApiResponses({@ApiResponse(code = 200, message = "成功")})
    @ApiImplicitParams({
            @ApiImplicitParam(name = "map", value = "请求参数", required = true, paramType = "body")
    })
    @PostMapping("/analyzeHtmlTemp")
    public void analyzeHtmlTemp(HttpServletResponse response, @RequestBody String map) {
        AtResponse resp = new AtResponse(System.currentTimeMillis());
        JiaheRuleBean jiaheRuleBean = warnService.analyzeEmrData2Jiaherulebean(map, null,socketPropConf.getIp(),socketPropConf.getPort2());
        Ruyuanjilu ruyuanjilu = jiaheRuleBean.getRuyuanjilu();
        List<Map<String, String>> ruyuanjiluMapList = new ArrayList<>(8);
        Optional.ofNullable(ruyuanjilu.getChief_complaint()).ifPresent(s -> ruyuanjiluMapList.add(ruyuanjiluMap("主诉", s)));
        Optional.ofNullable(ruyuanjilu.getAuxiliary_examination()).ifPresent(s -> ruyuanjiluMapList.add(ruyuanjiluMap("辅助检查", s)));
        Optional.ofNullable(ruyuanjilu.getHistory_of_family_member_diseases()).ifPresent(s -> ruyuanjiluMapList.add(ruyuanjiluMap("家族史", s)));
        Optional.ofNullable(ruyuanjilu.getHistory_of_past_illness()).ifPresent(s -> ruyuanjiluMapList.add(ruyuanjiluMap("既往史", s)));
        Optional.ofNullable(ruyuanjilu.getHistory_of_present_illness()).ifPresent(s -> ruyuanjiluMapList.add(ruyuanjiluMap("现病史", s)));
        Optional.ofNullable(ruyuanjilu.getSocial_history()).ifPresent(s -> ruyuanjiluMapList.add(ruyuanjiluMap("个人史", s)));
        Optional.ofNullable(ruyuanjilu.getMenstrual_and_obstetrical_histories()).ifPresent(s -> ruyuanjiluMapList.add(ruyuanjiluMap("婚育史", s)));
        Optional.ofNullable(ruyuanjilu.getPhysical_examination()).ifPresent(s -> ruyuanjiluMapList.add(ruyuanjiluMap("专科检查", s)));
        String string = JSONObject.toJSONString(jiaheRuleBean);
        JSONObject object = JSONObject.parseObject(string);
        object.put("ruyuanjilu", ruyuanjiluMapList);
        resp.setData(object);
        resp.setResponseCode(ResponseCode.OK);
        wirte(response, resp);
    }


    public Map<String, String> ruyuanjiluMap(String key, String value) {
        Map<String, String> tempMap = new HashMap<>(2);
        tempMap.put("key", key);
        tempMap.put("value", value);
        return tempMap;

    }

}
