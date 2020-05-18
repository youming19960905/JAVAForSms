package com.leyou.service;

import com.leyou.common.utils.NumberUtils;
import com.leyou.pojo.User;
import com.leyou.user.mapper.UserMapper;
import com.leyou.utils.CodecUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class UserService {
    @Autowired
    private UserMapper userMapper;

    @Autowired
    private AmqpTemplate amqpTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    static final String KEY_PREFIX = "user:code:phone:";

    public Boolean checkData(String data, Integer type) {
        System.out.println("123123123123:::"+type);
        User user = new User();
        if(type == 1){
            System.out.println("1111111111");
            user.setUsername(data);
            System.out.println("username:"+user.getUsername());
        }else if(type == 2){
            System.out.println("2222222222");
            user.setPhone(data);
            System.out.println("phone:"+user.getPhone());
        }else{
            return null;
        }
        System.out.println(this.userMapper.selectCount(user));
        return this.userMapper.selectCount(user) == 0;
    }

    //调用工具类发送6位验证码
    public void sendVerifyCode(String phone) {
        if(StringUtils.isBlank(phone)){
            return;
        }

        //生成验证码
        String code = NumberUtils.generateCode(6);

        //发送消息给rabbitmq
        Map<String , String> msg = new HashMap<>();
        msg.put("phone",phone);
        msg.put("code",code);
        this.amqpTemplate.convertAndSend("leyou.sms.exchange",
                                        "sms.verify.code",
                                        msg);

        System.out.println("123456123");
        //将验证码保存到Redis中
        this.redisTemplate.opsForValue().set(KEY_PREFIX+phone,code,5, TimeUnit.MINUTES);

    }

    public void register(User user, String code) {
        //1.查询redis中的验证码
        String s = this.redisTemplate.opsForValue().get(KEY_PREFIX + user.getPhone());
        if(!StringUtils.equals(code , s)){
            return;
        }

        //2.生成盐
        String salt = CodecUtils.generateSalt();
        user.setSalt(salt);

        //3.加盐加密
        user.setPassword(CodecUtils.md5Hex(user.getPassword() , salt) );

        //4.新增用户
        user.setId(null);
        user.setCreated(new Date());
        this.userMapper.insertSelective(user);
    }

    public User queryUser(String username, String password) {
        User record = new User();
        record.setUsername(username);
        User user = this.userMapper.selectOne(record);

        //判断user是否为空
        if(user == null){
            return null;
        }

        //获取盐，对用户密码加盐加密，拿此时加密后的密码和原先数据库中的密码比对
        password = CodecUtils.md5Hex(password,user.getSalt());
        if(StringUtils.equals(password,user.getPassword())){
            return user;
        }
        return null;
    }
}
