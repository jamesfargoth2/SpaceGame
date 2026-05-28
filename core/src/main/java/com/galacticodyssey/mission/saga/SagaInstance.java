package com.galacticodyssey.mission.saga;

public class SagaInstance {
    public String sagaId;
    public String sagaDataId;
    public String templateId;

    public SagaInstance() {}

    public SagaInstance(String sagaId, String sagaDataId, String templateId) {
        this.sagaId = sagaId;
        this.sagaDataId = sagaDataId;
        this.templateId = templateId;
    }
}
