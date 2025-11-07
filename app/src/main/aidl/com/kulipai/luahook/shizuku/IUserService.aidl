package com.kulipai.luahook.shizuku;

import com.kulipai.luahook.shizuku.ShellResult;

interface IUserService {
    ShellResult exec(String cmd);
    void destroy();
    void exit();
}
