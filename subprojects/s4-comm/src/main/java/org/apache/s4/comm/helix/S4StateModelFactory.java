package org.apache.s4.comm.helix;

import org.apache.helix.participant.statemachine.StateModelFactory;


public class S4StateModelFactory extends StateModelFactory<S4StateModel>{

	@Override
	public S4StateModel createNewStateModel(String partitionName) {
		return new S4StateModel(partitionName);
	}

}
