package oomdptb.oomdp;

public interface RewardFunction {
	
	//note that params are the parameters for the action
	public double reward(State s, GroundedAction a, State sprime);

}
