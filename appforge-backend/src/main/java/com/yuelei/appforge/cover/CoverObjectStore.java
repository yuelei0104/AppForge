package com.yuelei.appforge.cover;
import java.nio.file.Path;
public interface CoverObjectStore {
    void upload(String key,Path file);
    byte[] read(String key);
    void delete(String key);
}
