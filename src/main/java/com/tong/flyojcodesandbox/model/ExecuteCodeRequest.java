package com.tong.flyojcodesandbox.model;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecuteCodeRequest {
    /**
     * 输入参数
     */
    private List<String> inputList;

    /**
     * 用户代码
     */
    private String code;

    /**
     * 选择的编程语言
     */
    private String language;

}
