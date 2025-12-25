package com.kulipai.luahook.core.shizuku;

import com.kulipai.luahook.shizuku.ShellResult;

interface IUserService {
    ShellResult exec(String cmd);
    void destroy();
    void exit();
}
