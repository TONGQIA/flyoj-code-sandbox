package com.tong.flyojcodesandbox;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Scanner;

public class Main1 {

    public static void main(String[] args) {
//        int n = Integer.parseInt(args[0]);
//        int[] nums = new int[n];
//        for (int i = 0; i < n; i++) {
//            nums[i] = Integer.parseInt(args[i+1]);
//        }
//        int target = Integer.parseInt(args[n+1]);
//        int[] result = twoSum(nums, target);
//        System.out.println(Arrays.toString(result));

        Scanner scanner = new Scanner(System.in);
        int n = scanner.nextInt();
        int[] nums = new int[n];
        for (int i = 0; i < n; i++) {
            nums[i] = scanner.nextInt();
        }
        int target = scanner.nextInt();
        int[] result = twoSum(nums, target);
        System.out.println(Arrays.toString(result));
        scanner.close();
    }

    public static int[] twoSum(int[] nums, int target) {
        HashMap<Integer, Integer> map = new HashMap<>();

        for (int i = 0; i < nums.length; i++) {
            if (map.containsKey(target - nums[i])) {
                return new int[]{map.get(target - nums[i]), i};
            }
            map.put(nums[i], i);
        }
        return new int[]{};
    }
}