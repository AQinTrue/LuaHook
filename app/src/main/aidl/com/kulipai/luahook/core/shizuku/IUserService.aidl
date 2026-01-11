package com.kulipai.luahook.core.shizuku;

import com.kulipai.luahook.core.shizuku.ShizukuShellResult;

interface IUserService {
    ShizukuShellResult exec(String cmd);
    void destroy();
    void exit();
}
