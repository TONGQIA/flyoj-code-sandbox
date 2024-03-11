package com.tong.flyojcodesandbox.utils;

import cn.hutool.core.util.StrUtil;
import com.tong.flyojcodesandbox.model.ExecuteMassage;
import org.springframework.util.StopWatch;

import java.io.*;
import java.util.concurrent.TimeUnit;

public class ProcessUtils {
    /**
     * 执行进程并获取信息
     * @param process
     * @param opName
     * @return
     */
    public static ExecuteMassage runProcessAndGetMessage(Process process, String opName) {
        ExecuteMassage executeMassage = new ExecuteMassage();
        // 等待程序执行 获得错误码
        try {
            StopWatch stopWatch = new StopWatch();
            stopWatch.start();


            int exitValue = process.waitFor();
            executeMassage.setCode(exitValue);

            // 获取进程的正常输出
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder stringBuilder = new StringBuilder();
            // 分批逐行写入字符串
            String outputLine;
            while ((outputLine = bufferedReader.readLine()) != null) {
                stringBuilder.append(outputLine);
            }
            executeMassage.setMessage(stringBuilder.toString());
            // 正常退出
            if (exitValue == 0) {
                System.out.println(opName + "成功");
                //System.out.println(stringBuilder);
            } else {
                // 异常退出
                System.out.println(opName + "失败，错误码" + exitValue);
                // 获取进程的错误输出
                BufferedReader errorBufferedReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                StringBuilder errorStringBuilder = new StringBuilder();
                //分批逐行写入字符串
                String errorOutputLine;
                while ((errorOutputLine = errorBufferedReader.readLine())!= null) {
                    errorStringBuilder.append(errorOutputLine);
                }
                System.out.println(errorStringBuilder);
                executeMassage.setErrorMessage(errorStringBuilder.toString());
            }

            stopWatch.stop();
            executeMassage.setTime(stopWatch.getLastTaskTimeMillis());


        } catch (Exception e) {
            e.printStackTrace();
        }
        return executeMassage;
    }

    /**
     * 执行交互式进程并获取信息
     * @param process
     * @param opName
     * @param args
     * @return
     */
    public static ExecuteMassage runInteractProcessAndGetMessage(Process process, String opName, String args) {
        ExecuteMassage executeMassage = new ExecuteMassage();

        try {
            // TODO 需要优化

            // 拿到输入输出流
            InputStream inputStream = process.getInputStream();
            OutputStream outputStream = process.getOutputStream();

            // 往输出流写入数据
            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(outputStream);
            String[] s = args.split(" ");
            outputStreamWriter.write(StrUtil.join("\n",s) + "\n");
            //outputStreamWriter.write("1\n2\n");
            outputStreamWriter.flush();// 刷新，相当于回车

            // 获取进程的正常输出
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder stringBuilder = new StringBuilder();
            // 分批逐行写入字符串
            String outputLine;
            while ((outputLine = bufferedReader.readLine()) != null) {
                stringBuilder.append(outputLine);
            }
            executeMassage.setMessage(stringBuilder.toString());

            // 交互式的，应该在等待程序执行前，就获取输入
            // 等待程序执行 获得错误码
            int exitValue = process.waitFor();
            executeMassage.setCode(exitValue);
            // 正常退出
            if (exitValue == 0) {
                System.out.println(opName + "成功");
            } else {
                // 异常退出
                System.out.println(opName + "失败，错误码" + exitValue);

                // 获取进程的错误输出
                InputStream errorStream = process.getErrorStream();
                BufferedReader errorBufferedReader = new BufferedReader(new InputStreamReader(errorStream));
                StringBuilder errorStringBuilder = new StringBuilder();
                //分批逐行写入字符串
                String errorOutputLine;
                while ((errorOutputLine = errorBufferedReader.readLine())!= null) {
                    errorStringBuilder.append(errorOutputLine);
                }
                System.out.println(errorStringBuilder);
                executeMassage.setErrorMessage(errorStringBuilder.toString());
                errorStream.close();
            }
            outputStreamWriter.close();
            inputStream.close();
            outputStream.close();
            process.destroy();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return executeMassage;
    }
}
