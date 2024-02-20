package com.tong.flyojcodesandbox;

import cn.hutool.core.date.StopWatch;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.resource.ResourceUtil;
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
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class JavaDockerCodeSandboxOld implements CodeSandbox {

    public static final String GLOBAL_CODE_DIR_NAME = "tmpCode";
    public static final String GLOBAL_JAVA_CLASS_NAME = "Main.java";
    public static final long TIME_OUT = 5000L;
    public static boolean FIRST_INIT = true;

    public static void main(String[] args) {
        JavaDockerCodeSandboxOld javaNativeCodeSandbox = new JavaDockerCodeSandboxOld();
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


        saveFile result = getSaveFile(executeCodeRequest);


        // 2. 编译
        String compileCmd = String.format("javac -encoding utf-8 %s", result.userCodeFile.getAbsolutePath());
        // 后面需要用到进程的结果，所以要获得进程
        try {
            Process process = Runtime.getRuntime().exec(compileCmd);
            ExecuteMassage executeMassage = ProcessUtils.runProcessAndGetMessage(process, "编译");
            System.out.println(executeMassage);
        } catch (IOException e) {
            return getErrorResponse(e);
        }

        // 3. 创建容器，把文件复制到容器内
        // 获取默认的Docker Client
        DockerClient dockerClient = DockerClientBuilder.getInstance().build();

        String image = "amazoncorretto:8u402-al2023-jre";
        //String image = "openjdk:8-alpine";
        // 3.1 拉取镜像 只拉取一次
        if (FIRST_INIT) {
            PullImageCmd pullImageCmd = dockerClient.pullImageCmd(image);
            PullImageResultCallback pullImageResultCallback = new PullImageResultCallback(){
                @Override
                public void onNext(PullResponseItem item) {
                    System.out.println("下载镜像：" + item.getStatus());
                    super.onNext(item);
                }
            };
            try {
                pullImageCmd.exec(pullImageResultCallback)
                        .awaitCompletion();
            } catch (InterruptedException e) {
                System.out.println("拉取镜像异常");
                throw new RuntimeException(e);
            }
            System.out.println("镜像下载完成");
            FIRST_INIT = false;
        }
        else {
            System.out.println("镜像已下载过");
        }

        // 3.2 创建容器，把文件复制到容器内


        HostConfig hostConfig = new HostConfig();
        // 容器挂载目录
        hostConfig.setBinds(new Bind(result.userCodeParentPath, new Volume("/app")));
        // 容器限制内存和CPU
        hostConfig.withMemorySwap(0L);//禁用容器的swap空间
        hostConfig.withMemory(100 * 1000 * 1000L);
        hostConfig.withCpuCount(1L);
        hostConfig.withReadonlyRootfs(true);// 权限管理 限制用户不能向root根目录写文件

        CreateContainerCmd containerCmd = dockerClient.createContainerCmd(image);
        CreateContainerResponse createContainerResponse = containerCmd
                .withHostConfig(hostConfig)
                .withNetworkDisabled(true)// 关闭网络配置
                .withAttachStdin(true)
                .withAttachStderr(true)
                .withAttachStdout(true)
                .withTty(true)
                .exec();
        System.out.println(createContainerResponse);
        String containerId = createContainerResponse.getId();

        // 4. 在容器中执行代码，得到输出结果
        // 启动容器
        dockerClient.startContainerCmd(containerId).exec();

        List<ExecuteMassage> executeMassagesList = new ArrayList<>();

        // docker exec condescending_moser java -cp /app Main 1 3
        for (String inputArgs : result.inputList) {
            StopWatch stopWatch = new StopWatch();
            String[] inputArgsArray = inputArgs.split(" ");
            String[] cmdArray = ArrayUtil.append(new String[]{"java", "-cp", "/app", "Main"}, inputArgsArray);
            ExecCreateCmdResponse execCreateCmdResponse = dockerClient.execCreateCmd(containerId)
                    .withCmd(cmdArray)
                    .withAttachStderr(true)
                    .withAttachStdin(true)
                    .withAttachStdout(true)
                    .exec();
            System.out.println("创建执行命令：" + execCreateCmdResponse);

            final String[] massage = {null};
            final String[] errorMassage = {null};
            long time = 0L;
            final boolean[] timeout = {true};
            // 创建回调
            ExecStartResultCallback execStartResultCallback = new ExecStartResultCallback(){
                @Override
                public void onComplete() {
                    // 如果执行完成，就没超时
                    timeout[0] = false;
                    super.onComplete();
                }

                @Override
                public void onNext(Frame frame) {
                    StreamType streamType = frame.getStreamType();
                    if (StreamType.STDERR.equals(streamType)){
                        errorMassage[0] = new String(frame.getPayload());
                        System.out.println("输出错误结果：" + errorMassage[0]);
                    } else {
                        massage[0] = new String(frame.getPayload());
                        System.out.println("输出结果：" + massage[0]);
                    }
                    super.onNext(frame);
                }

            };

            // 获取占用的内存
            final long[] maxMemory = {0L};
            StatsCmd statsCmd = dockerClient.statsCmd(containerId);
            ResultCallback<Statistics> statisticsResultCallback = statsCmd.exec(new ResultCallback<Statistics>() {
                @Override
                public void close() throws IOException {

                }

                @Override
                public void onStart(Closeable closeable) {

                }

                @Override
                public void onNext(Statistics statistics) {
                    long usage = statistics.getMemoryStats().getUsage();
                    System.out.println("内存占用：" + usage);
                    maxMemory[0] = Math.max(usage, maxMemory[0]);
                }

                @Override
                public void onError(Throwable throwable) {

                }

                @Override
                public void onComplete() {

                }
            });
            final ExecuteMassage executeMassage = new ExecuteMassage();
            try {
                stopWatch.start();
                dockerClient.execStartCmd(execCreateCmdResponse.getId())
                        .exec(execStartResultCallback)
                        .awaitCompletion(TIME_OUT, TimeUnit.MILLISECONDS);
                stopWatch.stop();
                time =Math.max(stopWatch.getLastTaskTimeMillis(), time);
                statsCmd.close();
                executeMassage.setTime(time);
                executeMassage.setMessage(massage[0]);
                executeMassage.setErrorMessage(errorMassage[0]);
                executeMassagesList.add(executeMassage);
                executeMassage.setMemory(maxMemory[0]);
                executeMassagesList.add(executeMassage);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            if (timeout[0]){
                break;// 如果超时，退出
            }
        }

        // 5. 收集整理并且输出结果
        // 收集并整理返回信息
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        executeCodeResponse.setStatus(1);
        JudgeInfo judgeInfo = new JudgeInfo();

        // 6. 文件清理，释放空间

        // 7. 错误处理


        return executeCodeResponse;
    }

    private static saveFile getSaveFile(ExecuteCodeRequest executeCodeRequest) {
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
        saveFile result = new saveFile(inputList, userCodeParentPath, userCodeFile);
        return result;
    }

    private static class saveFile {
        public final List<String> inputList;
        public final String userCodeParentPath;
        public final File userCodeFile;

        public saveFile(List<String> inputList, String userCodeParentPath, File userCodeFile) {
            this.inputList = inputList;
            this.userCodeParentPath = userCodeParentPath;
            this.userCodeFile = userCodeFile;
        }
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
