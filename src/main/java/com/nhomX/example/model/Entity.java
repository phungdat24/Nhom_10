package com.nhomX.example.model;

import java.io.Serializable;
import java.util.UUID;

public abstract class Entity implements Serializable {
    // PROTECTED ĐỂ CÁC LỚP CON TRUY CẬP:
    protected String id;
    //Hàm tạo rỗng(ĐỂ ĐỌC DỮ LIỆU TỪ DB):
    public Entity(){
    }
    // Hàm khởi tạo có tham số (LOAD dữ liệu từ database lên):
    public Entity(String id){
        this.id = id;
    }
    // Sinh ID mới ngẫu nhiên (UUID) nếu sinh ID mới hoàn toàn:
    public void generateId(){
        if(this.id == null || this.id.isEmpty()){
            this.id = UUID.randomUUID().toString();
        }
    }
    // Getters và Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }
}
