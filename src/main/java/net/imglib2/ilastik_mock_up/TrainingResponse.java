package net.imglib2.ilastik_mock_up;

import javax.json.bind.annotation.JsonbProperty;

public class TrainingResponse {

	@JsonbProperty
	private String trainingId;

	public String getTrainingId() {
		return trainingId;
	}

	public void setTrainingId(String trainingId) {
		this.trainingId = trainingId;
	}
}
