package com.jhmk.cloudpage.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.jhmk.cloudentity.earlywaring.entity.repository.service.YizhuRepService;
import com.jhmk.cloudentity.earlywaring.entity.rule.Jianchabaogao;
import com.jhmk.cloudentity.earlywaring.entity.rule.Jianyanbaogao;
import com.jhmk.cloudentity.earlywaring.entity.rule.Rule;
import com.jhmk.cloudentity.earlywaring.entity.rule.Yizhu;
import com.jhmk.cloudservice.warnService.service.*;
import com.jhmk.cloudutil.util.StringUtil;
import com.netflix.discovery.converters.Auto;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * @author ziyu.zhou
 * @date 2019/1/11 13:40
 * 提醒页面服务层
 */

@Service
public class WarnService {
    private static final Logger logger = LoggerFactory.getLogger(WarnService.class);

    @Autowired
    JianyanbaogaoService jianyanbaogaoService;
    @Autowired
    JianchabaogaoService jianchabaogaoService;
    @Autowired
    YizhuRepService yizhuRepService;
    @Autowired
    YizhuService yizhuService;
    @Autowired
    RuleService ruleService;

    public void ruleMatch(String data, String hospitalName) {
        JSONObject object = JSON.parseObject(data);
        logger.info("接受到的初始数据{}", JSONObject.toJSONString(object));
        //页面来源 入院记录： 0 :保存病历 1：下诊断 2：打开病例 3：新建病例 6：下医嘱 7：病案首页 8：其他
        String pageSource = object.getString("pageSource");//页面来源
        logger.info("页面来源：{}", pageSource);
        Rule rule = getRule(data, hospitalName);
        //获取响应结果
        String ruleMatchGetResp = ruleService.ruleMatchGetResp(rule);



    }

    public Rule getRule(String data, String hospital) {
        JSONObject object = JSONObject.parseObject(data);
        String pageSource = object.getString("pageSource");//页面来源
        if (StringUtils.isEmpty(pageSource) || "test".equals(pageSource)) {
            Map<String, String> paramMap = (Map) JSON.parse(data);
            String s = ruleService.anaRule(paramMap);
            String replace = StringUtil.stringTransform(s);
            JSONObject parse = JSONObject.parseObject(replace);
            Rule rule = Rule.fill(parse);
            return rule;
        } else if ("6".equals(pageSource)) {//医嘱 为6 其他做下诊断处理
            JSONObject jsonObject = JSONObject.parseObject(data);
            List<Yizhu> yizhus = yizhuService.getYizhu(data, hospital);
            if (Objects.nonNull(jsonObject)) {
                String patient_id = jsonObject.getString("patient_id");
                String visit_id = jsonObject.getString("visit_id");
                String doctor_id = jsonObject.getString("doctor_id");
                Rule rule = ruleService.getDiagnoseFromDatabase(patient_id, visit_id);
                rule.setYizhu(yizhus);
                rule.setDoctor_id(doctor_id);
                //获取 拼接检验检查报告
                List<Jianyanbaogao> jianyanbaogao = jianyanbaogaoService.getJianyanbaogao(rule, hospital);
                rule.setJianyanbaogao(jianyanbaogao);
                List<Jianchabaogao> jianchabaogao = jianchabaogaoService.getJianchabaogao(rule, hospital);
                rule.setJianchabaogao(jianchabaogao);
                return rule;

            }
        } else {
            Map<String, String> parse = (Map) JSONObject.parse(data);
            String s = ruleService.anaRule(parse);
            //解析一诉五史
            Rule rule = Rule.fill(object);
            //获取 拼接检验检查报告
            List<Jianyanbaogao> jianyanbaogao = jianyanbaogaoService.getJianyanbaogao(rule, hospital);
            rule.setJianyanbaogao(jianyanbaogao);
            List<Jianchabaogao> jianchabaogao = jianchabaogaoService.getJianchabaogao(rule, hospital);
            rule.setJianchabaogao(jianchabaogao);
            //从数据库获取 如果数据可没有 从数据中心获取
            List<Yizhu> yizhuList = yizhuService.getYizhu(data, hospital);
            rule.setYizhu(yizhuList);
            return rule;
        }
        return null;
    }

}
