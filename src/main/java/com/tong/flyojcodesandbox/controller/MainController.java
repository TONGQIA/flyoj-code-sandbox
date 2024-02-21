package com.tong.flyojcodesandbox.controller;

import cn.hutool.http.server.HttpServerRequest;
import com.tong.flyojcodesandbox.JavaNativeCodeSandbox;
import com.tong.flyojcodesandbox.model.ExecuteCodeRequest;
import com.tong.flyojcodesandbox.model.ExecuteCodeResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@RestController("/")
public class MainController {

    @Resource
    JavaNativeCodeSandbox javaNativeCodeSandbox;

    //定义鉴权请求头和密钥
    public static final String AUTH_REQUEST_HEADER = "auth";
    public static final String AUTH_REQUEST_SECRET = "secretKey";


    @GetMapping("health")
    public String healthCheck(){
        return "ok";
    }

    /**
     *  执行代码
     * @param executeCodeRequest
     * @return
     */
    @PostMapping("/executeCode")
    public ExecuteCodeResponse executeCode(@RequestBody ExecuteCodeRequest executeCodeRequest, HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse){
        // 1. 鉴权
        String authHeader = httpServletRequest.getHeader(AUTH_REQUEST_HEADER);
        if (!AUTH_REQUEST_SECRET.equals(authHeader)){
            httpServletResponse.setStatus(403);
            return null;
        }
        // 2. 判空
        if (executeCodeRequest == null){
            ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
            executeCodeResponse.setStatus(2);
            executeCodeResponse.setMessage("请求参数为空");
            return executeCodeResponse;
        }
        // 3. 执行
        return javaNativeCodeSandbox.executeCode(executeCodeRequest);
    }
}
