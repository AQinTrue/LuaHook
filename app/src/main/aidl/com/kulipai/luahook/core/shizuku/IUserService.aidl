package com.kulipai.luahook.core.shizuku;

import com.kulipai.luahook.core.shizuku.ShellResult;

interface IUserService {
    ShellResult exec(String cmd);
    void destroy();
    void exit();
}
