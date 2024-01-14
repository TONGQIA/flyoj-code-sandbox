package com.tong.flyojcodesandbox;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import com.tong.flyojcodesandbox.model.ExecuteCodeRequest;
import com.tong.flyojcodesandbox.model.ExecuteCodeResponse;
import com.tong.flyojcodesandbox.model.ExecuteMassage;
import com.tong.flyojcodesandbox.model.JudgeInfo;
import com.tong.flyojcodesandbox.utils.ProcessUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

public class JavaNativeCodeSandbox implements CodeSandbox {

    public static final String GLOBAL_CODE_DIR_NAME = "tmpCode";
    public static final String GLOBAL_JAVA_CLASS_NAME = "Main.java";

    public static void main(String[] args) {
        JavaNativeCodeSandbox javaNativeCodeSandbox = new JavaNativeCodeSandbox();
        ExecuteCodeRequest executeCodeRequest = new ExecuteCodeRequest();
        executeCodeRequest.setInputList(Arrays.asList("1 2", "1 3"));
        String code = ResourceUtil.readUtf8Str("testCode" + File.separator + "simpleComputeArgs" + File.separator + "Main.java");
        executeCodeRequest.setCode(code);
        executeCodeRequest.setLanguage("java");
        ExecuteCodeResponse executeCodeResponse = javaNativeCodeSandbox.executeCode(executeCodeRequest);
        System.out.println(executeCodeResponse);

    }

    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {

        // 1. 保存文件
        List<String> inputList = executeCodeRequest.getInputList();
        String code = executeCodeRequest.getCode();
        String language = executeCodeRequest.getLanguage();

        // 1.1 新增目录
        // 路径
        String userDir = System.getProperty("user.dir");
        String globalCodePathName = userDir + File.separator + GLOBAL_CODE_DIR_NAME;

        if (!FileUtil.exist(globalCodePathName)) {
            FileUtil.mkdir(globalCodePathName);
        }
        // 1.2 将每个用户的代码都存放在独立目录下，通过UUID随机生成目录名，便于隔离和维护
        String userCodeParentPath = globalCodePathName + File.separator + UUID.randomUUID();
        String userCodePath = userCodeParentPath + File.separator + GLOBAL_JAVA_CLASS_NAME;
        File userCodeFile = FileUtil.writeString(code, userCodePath, StandardCharsets.UTF_8);
        //

        // 2. 编译
        String compileCmd = String.format("javac -encoding utf-8 %s", userCodeFile.getAbsolutePath());
        // 后面需要用到进程的结果，所以要获得进程
        try {
            Process process = Runtime.getRuntime().exec(compileCmd);
            ExecuteMassage executeMassage = ProcessUtils.runProcessAndGetMessage(process, "编译");
            System.out.println(executeMassage);
        } catch (IOException e) {
            return getErrorResponse(e);
        }

        // TODO 这里循环运行？是否可以优化
        // 3. 运行（权限、资源等安全问题）
        List<ExecuteMassage> executeMassagesList = new ArrayList<>();
        for (String inputArgs : inputList) {
            String runCmd = String.format("java -Dfile.encoding=UTF-8 -cp %s Main %s", userCodeParentPath, inputArgs);
            try {
                Process process = Runtime.getRuntime().exec(runCmd);
                ExecuteMassage executeMassage = ProcessUtils.runProcessAndGetMessage(process, "运行");
                //ExecuteMassage executeMassage = ProcessUtils.runInteractProcessAndGetMessage(process, "运行", inputArgs);
                System.out.println(executeMassage);
                executeMassagesList.add(executeMassage);
            } catch (Exception e) {
                return getErrorResponse(e);
            }
        }

        // 4. 收集并整理返回信息
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        executeCodeResponse.setStatus(1);
        List<String> outputList = new ArrayList<>();
        long maxTime = 0;
        for (ExecuteMassage executeMassage : executeMassagesList) {
            // 操作outputList
            if (executeMassage.getCode() == 0) {
                //outputList = executeMassagesList.stream().map(ExecuteMassage::getMessage).collect(Collectors.toList());
                outputList.add(executeMassage.getMessage());
            } else {
                //outputList = executeMassagesList.stream().map(ExecuteMassage::getErrorMessage).collect(Collectors.toList());
                outputList.add(executeMassage.getErrorMessage());
                // TODO 创建枚举值
                executeCodeResponse.setStatus(3);
                // TODO 优化
                executeCodeResponse.setMessage(executeMassage.getErrorMessage());
            }

            // 操作maxTime
            maxTime = Math.max(maxTime, executeMassage.getTime());

        }
        executeCodeResponse.setOutputList(outputList);

        JudgeInfo judgeInfo = new JudgeInfo();
        // TODO
        //judgeInfo.setMemory();
        judgeInfo.setTime(maxTime);

        executeCodeResponse.setJudgeInfo(judgeInfo);

        // 5. 文件清理
        boolean del = FileUtil.del(userCodeFile.getParentFile());
        System.out.println(del ? "删除成功" : "删除失败");

        return executeCodeResponse;
    }

    /**
     * 获取错误响应
     * @param e
     * @return
     */
    private ExecuteCodeResponse getErrorResponse(Throwable e) {
        // 6. 错误处理
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        executeCodeResponse.setOutputList(new ArrayList<>());
        executeCodeResponse.setMessage(e.getMessage());
        // TODO 枚举
        executeCodeResponse.setStatus(2);
        executeCodeResponse.setJudgeInfo(new JudgeInfo());
        return executeCodeResponse;
    }

}
