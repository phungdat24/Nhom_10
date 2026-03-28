package com.nhomX.example.model;

public class GeneralItem extends Items {
    public GeneralItem(String id, String title, String sellerId){
        super(id, title, sellerId);
    }
    public String toString(){
        return "General Item: "+ getTitle();
    }
}
