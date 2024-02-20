package com.tong.flyojcodesandbox;

import cn.hutool.core.date.StopWatch;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.ArrayUtil;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import com.tong.flyojcodesandbox.model.ExecuteCodeRequest;
import com.tong.flyojcodesandbox.model.ExecuteCodeResponse;
import com.tong.flyojcodesandbox.model.ExecuteMassage;
import com.tong.flyojcodesandbox.model.JudgeInfo;
import com.tong.flyojcodesandbox.utils.ProcessUtils;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class JavaCodeSandboxTemplate implements CodeSandbox {

    public static final String GLOBAL_CODE_DIR_NAME = "tmpCode";
    public static final String GLOBAL_JAVA_CLASS_NAME = "Main.java";

    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {


        List<String> inputList = executeCodeRequest.getInputList();
        String code = executeCodeRequest.getCode();
        String language = executeCodeRequest.getLanguage();
        // 1. 把用户代码保存为文件
        File userCodeFile = saveCodeFile(code);

        // 2. 编译
        ExecuteMassage compileFileExecuteMassage = compileFile(userCodeFile);
        System.out.println(compileFileExecuteMassage);

        // TODO 这里循环运行？是否可以优化
        // 3. 运行（权限、资源等安全问题）
        List<ExecuteMassage> executeMassagesList = runFile(inputList, userCodeFile);


        // 4. 收集并整理返回信息
        ExecuteCodeResponse executeCodeResponse = getExecuteCodeResponse(executeMassagesList);

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
    private static File saveCodeFile(String code) {
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
    private ExecuteMassage compileFile(File userCodeFile) {
        String compileCmd = String.format("javac -encoding utf-8 %s", userCodeFile.getAbsolutePath());
        // 后面需要用到进程的结果，所以要获得进程
        try {
            Process process = Runtime.getRuntime().exec(compileCmd);
            ExecuteMassage executeMassage = ProcessUtils.runProcessAndGetMessage(process, "编译");
            if (executeMassage.getCode() != 0) {
                throw new RuntimeException("编译错误");
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
    private List<ExecuteMassage> runFile(List<String> inputList, File userCodeFile) {
        List<ExecuteMassage> executeMassagesList = new ArrayList<>();
        for (String inputArgs : inputList) {
            // 运行时需要限制内存
            String runCmd = String.format("java -Xmx256m -Dfile.encoding=UTF-8 -cp %s Main %s", userCodeFile.getParentFile().getAbsoluteFile(), inputArgs);
            try {
                Process process = Runtime.getRuntime().exec(runCmd);
                ExecuteMassage executeMassage = ProcessUtils.runProcessAndGetMessage(process, "运行");
                //ExecuteMassage executeMassage = ProcessUtils.runInteractProcessAndGetMessage(process, "运行", inputArgs);
                System.out.println(executeMassage);
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
    private static ExecuteCodeResponse getExecuteCodeResponse(List<ExecuteMassage> executeMassagesList) {
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
        return executeCodeResponse;
    }


    /**
     * 5. 文件清理
     * @param userCodeFile
     * @return
     */
    private static boolean extracted(File userCodeFile) {
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
        executeCodeResponse.setStatus(2);
        executeCodeResponse.setJudgeInfo(new JudgeInfo());
        return executeCodeResponse;
    }
}
