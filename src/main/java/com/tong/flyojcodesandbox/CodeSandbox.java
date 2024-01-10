package com.tong.flyojcodesandbox;

import com.tong.flyojcodesandbox.model.ExecuteCodeRequest;
import com.tong.flyojcodesandbox.model.ExecuteCodeResponse;

/**
 * 代码沙箱接口
 *
 * @author tong
 */
public interface CodeSandbox {
    /**
     * 执行代码
     *
     * @param executeCodeRequest
     * @return
     */
    ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest);

}
