package com.tong.flyojcodesandbox.utils;

import com.tong.flyojcodesandbox.model.ExecuteMassage;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class ProcessUtils {
    public static ExecuteMassage runProcessAndGetMessage(Process process, String opName) {
        ExecuteMassage executeMassage = new ExecuteMassage();
        // 等待程序执行 获得错误码
        try {
            int exitValue = process.waitFor();
            executeMassage.setCode(exitValue);
            // 正常退出
            if (exitValue == 0) {
                System.out.println(opName + "成功");
                // 获取进程的正常输出
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                StringBuilder stringBuilder = new StringBuilder();
                // 分批逐行写入字符串
                String outputLine;
                while ((outputLine = bufferedReader.readLine()) != null) {
                    stringBuilder.append(outputLine);
                }
                executeMassage.setMessage(stringBuilder.toString());
            } else {
                // 异常退出
                System.out.println(opName + "失败，错误码" + exitValue);
                // 获取进程的正常输出
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                StringBuilder stringBuilder = new StringBuilder();
                // 分批逐行写入字符串
                String outputLine;
                while ((outputLine = bufferedReader.readLine()) != null) {
                    stringBuilder.append(outputLine);
                }
                executeMassage.setMessage(stringBuilder.toString());

                // 获取进程的错误输出
                BufferedReader errorBufferedReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                StringBuilder errorStringBuilder = new StringBuilder();
                //分批逐行写入字符串
                String errorOutputLine;
                while ((errorOutputLine = errorBufferedReader.readLine())!= null) {
                    errorStringBuilder.append(errorOutputLine);
                }
                executeMassage.setErrorMessage(errorBufferedReader.toString());
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return executeMassage;
    }
}
