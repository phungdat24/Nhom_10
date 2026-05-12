package com.nhomX.example.model;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public abstract class Entity implements Serializable {
    private String id;
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
    /**
     * [THÊM MỚI] Override equals/hashCode dựa trên id.
     * Bắt buộc để so sánh đối tượng đúng cách (ví dụ: kiểm tra winner, so sánh trong Set).
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Entity entity = (Entity) o;
        return Objects.equals(id, entity.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

}
