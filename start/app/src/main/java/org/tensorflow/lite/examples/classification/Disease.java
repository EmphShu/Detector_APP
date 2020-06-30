package org.tensorflow.lite.examples.classification;

public class Disease {
    private String name;
    private String solution;
    public Disease(){}
    public Disease(String name,String solution){
        this.name=name;
        this.solution=solution;
    }
    public String getName(){
        return name;
    }
    public void setName(String name){
        this.name=name;
    }
    public String getSolution(){
        return solution;
    }
    public void setSolution(String solution){
        this.solution=solution;
    }
}
