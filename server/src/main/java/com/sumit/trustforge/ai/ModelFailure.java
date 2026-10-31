package com.sumit.trustforge.ai;

public final class ModelFailure extends RuntimeException {
    private final String classification;
    private final int retries;
    private final Integer transportAttempts;
    public ModelFailure(String message){this("PROVIDER_FAILURE",message);}
    public ModelFailure(String classification,String message){this(classification,message,0);}
    public ModelFailure(String classification,String message,int retries){this(classification,message,retries,null);}
    public ModelFailure(String classification,String message,int retries,Integer attempts){super(message);this.classification=classification;this.retries=retries;this.transportAttempts=attempts;}
    public Integer transportAttempts(){return transportAttempts;}
    public int retries(){return retries;}
    public String classification(){return classification;}
}
