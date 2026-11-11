package com.yuelei.appforge.service;

import jakarta.servlet.http.HttpServletResponse;

import java.nio.file.Path;

public interface ProjectDownloadService {

    void downloadProjectAsZip(String projectPath, String downloadFileName, HttpServletResponse response);
}
