package com.tong.flyojcodesandbox;

import cn.hutool.core.io.FileUtil;

import com.tong.flyojcodesandbox.common.ExecuteStatusEnum;
import com.tong.flyojcodesandbox.model.ExecuteCodeRequest;
import com.tong.flyojcodesandbox.model.ExecuteCodeResponse;
import com.tong.flyojcodesandbox.model.ExecuteMassage;
import com.tong.flyojcodesandbox.model.JudgeInfo;
import com.tong.flyojcodesandbox.utils.ProcessUtils;


import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class JavaCodeSandboxTemplate implements CodeSandbox {

    public static final String GLOBAL_CODE_DIR_NAME = "tmpCode";
    public static final String GLOBAL_JAVA_CLASS_NAME = "Main.java";

    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {

        ExecuteCodeResponse executeCodeResponse = null;

        List<String> inputList = executeCodeRequest.getInputList();
        String code = executeCodeRequest.getCode();
        String language = executeCodeRequest.getLanguage();
        // 1. 把用户代码保存为文件
        File userCodeFile = saveCodeFile(code);

        // 2. 编译
        ExecuteMassage compileFileExecuteMassage = compileFile(userCodeFile);

        // 编译成功才运行
        if (compileFileExecuteMassage.getCode().equals(0)){
            // TODO 这里循环运行？是否可以优化
            // 3. 运行（权限、资源等安全问题）
            List<ExecuteMassage>  executeMassagesList = runFile(inputList, userCodeFile);
            // 4. 收集并整理返回信息
            executeCodeResponse = getExecuteCodeResponse(executeMassagesList);
        }else {
            // 编译失败
            List<ExecuteMassage>  executeMassagesList  = new ArrayList<>();
            executeMassagesList.add(compileFileExecuteMassage);
            // 4. 收集并整理返回信息
            executeCodeResponse = getExecuteCodeResponse(executeMassagesList);
        }

        // 5. 文件清理
        boolean del = extracted(userCodeFile);
        System.out.println(del ? "删除成功" : "删除失败");

        return executeCodeResponse;
    }

    /**
     * 1. 保存文件
     * @param code
     * @return
     */
    public File saveCodeFile(String code) {
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

        return userCodeFile;
    }

    /**
     * 2. 编译
     * @param userCodeFile
     * @return
     */
    public ExecuteMassage compileFile(File userCodeFile) {
        String compileCmd = String.format("javac -encoding utf-8 %s", userCodeFile.getAbsolutePath());
        // 后面需要用到进程的结果，所以要获得进程
        try {
            Process process = Runtime.getRuntime().exec(compileCmd);
            ExecuteMassage executeMassage = ProcessUtils.runProcessAndGetMessage(process, "编译");
            if (executeMassage.getCode() != 0) {
                executeMassage.setErrorMessage(executeMassage.getErrorMessage());
                executeMassage.setCode(ExecuteStatusEnum.COMPILE_ERROR.getCode());
            }
            return executeMassage;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    /**
     * 3. 运行（权限、资源等安全问题）
     * @param inputList
     * @param userCodeFile
     * @return
     */
    public List<ExecuteMassage> runFile(List<String> inputList, File userCodeFile) {
        List<ExecuteMassage> executeMassagesList = new ArrayList<>();
        for (String inputArgs : inputList) {
            // 运行时需要限制内存
            String runCmd = String.format("java -Xmx256m -Dfile.encoding=UTF-8 -cp %s Main %s", userCodeFile.getParentFile().getAbsoluteFile(), inputArgs);
            System.out.println(runCmd);
            try {
                Process process = Runtime.getRuntime().exec(runCmd);
                //ExecuteMassage executeMassage = ProcessUtils.runProcessAndGetMessage(process, "运行");
                ExecuteMassage executeMassage = ProcessUtils.runInteractProcessAndGetMessage(process, "运行", inputArgs);
                executeMassagesList.add(executeMassage);
            } catch (Exception e) {
                throw new RuntimeException("程序执行异常:", e);
            }
        }
        return executeMassagesList;
    }

    /**
     * 4. 收集并整理返回信息
     * @param executeMassagesList
     * @return
     */
    public ExecuteCodeResponse getExecuteCodeResponse(List<ExecuteMassage> executeMassagesList) {
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        executeCodeResponse.setExecuteStatus(ExecuteStatusEnum.SUCCESS.getCode());
        List<String> outputList = new ArrayList<>();
        long maxTime = 0;
        long maxMemory = 0;
        for (ExecuteMassage executeMassage : executeMassagesList) {
            // 操作outputList
            if (executeMassage.getCode() == 0) {
                // 运行正常
                //outputList = executeMassagesList.stream().map(ExecuteMassage::getMessage).collect(Collectors.toList());
                outputList.add(executeMassage.getMessage());
        } else if (executeMassage.getCode() == 1) {
                // 编译错误
                outputList.add(executeMassage.getErrorMessage());
                executeCodeResponse.setExecuteStatus(ExecuteStatusEnum.COMPILE_ERROR.getCode());
                executeCodeResponse.setMessage(executeMassage.getErrorMessage());
                executeCodeResponse.setJudgeInfo(new JudgeInfo());
                return executeCodeResponse;
            } else {
                // 运行错误
                //outputList = executeMassagesList.stream().map(ExecuteMassage::getErrorMessage).collect(Collectors.toList());
                outputList.add(executeMassage.getErrorMessage());
                executeCodeResponse.setExecuteStatus(ExecuteStatusEnum.RUN_ERROR.getCode());
                executeCodeResponse.setMessage(executeMassage.getErrorMessage());
            }

            // 操作maxTime
            maxTime = Math.max(maxTime, executeMassage.getTime());
            maxMemory = Math.max(maxMemory, executeMassage.getMemory());

        }
        executeCodeResponse.setOutputList(outputList);

        JudgeInfo judgeInfo = new JudgeInfo();
        // TODO
        judgeInfo.setMemory(maxMemory);
        judgeInfo.setTime(maxTime);

        executeCodeResponse.setJudgeInfo(judgeInfo);
        return executeCodeResponse;
    }


    /**
     * 5. 文件清理
     * @param userCodeFile
     * @return
     */
    public boolean extracted(File userCodeFile) {
        if (userCodeFile.getParentFile() != null) {
            boolean del = FileUtil.del(userCodeFile.getParentFile());
            return del;
        }
        return true;
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
        executeCodeResponse.setExecuteStatus(2);
        executeCodeResponse.setJudgeInfo(new JudgeInfo());
        return executeCodeResponse;
    }
}
