package oomdptb.behavior.learning.tdmethods;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import oomdptb.behavior.learning.LearningAgent;
import oomdptb.behavior.options.Option;
import oomdptb.behavior.EpisodeAnalysis;
import oomdptb.behavior.Policy;
import oomdptb.behavior.QValue;
import oomdptb.behavior.planning.OOMDPPlanner;
import oomdptb.behavior.planning.QComputablePlanner;
import oomdptb.behavior.planning.StateHashTuple;
import oomdptb.behavior.planning.commonpolicies.EpsilonGreedy;
import oomdptb.oomdp.Attribute;
import oomdptb.oomdp.Domain;
import oomdptb.oomdp.GroundedAction;
import oomdptb.oomdp.RewardFunction;
import oomdptb.oomdp.State;
import oomdptb.oomdp.TerminalFunction;

public class QLearning extends OOMDPPlanner implements QComputablePlanner, LearningAgent{

	protected Map<StateHashTuple, QLearningStateNode>				qIndex;
	protected double												qInit;
	protected double												learningRate;
	protected Policy												learningPolicy;
	
	protected int													maxEpisodeSize;
	protected int													eStepCounter;
	
	
	protected int													numEpisodesForPlanning;
	protected double												maxQChangeForPlanningTermination;
	protected double												maxQChangeInLastEpisode;
	
	protected LinkedList<EpisodeAnalysis>							episodeHistory;
	protected int													numEpisodesToStore;
	
	
	
	public QLearning(Domain domain, RewardFunction rf, TerminalFunction tf, double gamma, Map <String, List<Attribute>> attributesForHashCode, 
			double qInit, double learningRate, int maxEpisodeSize) {
		this.QLInit(domain, rf, tf, gamma, attributesForHashCode, qInit, learningRate, new EpsilonGreedy(this, 0.1), maxEpisodeSize);
	}
	
	public QLearning(Domain domain, RewardFunction rf, TerminalFunction tf, double gamma, Map <String, List<Attribute>> attributesForHashCode, 
			double qInit, double learningRate, Policy learningPolicy, int maxEpisodeSize) {
		this.QLInit(domain, rf, tf, gamma, attributesForHashCode, qInit, learningRate, learningPolicy, maxEpisodeSize);
	}
	
	
	public void QLInit(Domain domain, RewardFunction rf, TerminalFunction tf, double gamma, Map <String, List<Attribute>> attributesForHashCode, 
			double qInit, double learningRate, Policy learningPolicy, int maxEpisodeSize){
		
		this.PlannerInit(domain, rf, tf, gamma, attributesForHashCode);
		this.qIndex = new HashMap<StateHashTuple, QLearningStateNode>();
		this.learningRate = learningRate;
		this.learningPolicy = learningPolicy;
		this.maxEpisodeSize = maxEpisodeSize;
		
		numEpisodesToStore = 1;
		episodeHistory = new LinkedList<EpisodeAnalysis>();
		
		numEpisodesForPlanning = 1;
		maxQChangeForPlanningTermination = 0.;
		
	}
	
	
	public void setLearningPolicy(Policy p){
		this.learningPolicy = p;
	}
	
	
	public void setMaxEpisodesForPlanning(int n){
		if(n > 0){
			this.numEpisodesForPlanning = n;
		}
		else{
			this.numEpisodesForPlanning = 1;
		}
	}
	
	public void setMaxQChangeForPlanningTerminaiton(double m){
		if(m > 0.){
			this.maxQChangeForPlanningTermination = m;
		}
		else{
			this.maxQChangeForPlanningTermination = 0.;
		}
	}
	
	public int getLastNumSteps(){
		return eStepCounter;
	}

	@Override
	public List<QValue> getQs(State s) {
		return this.getQs(this.stateHash(s));
	}

	@Override
	public QValue getQ(State s, GroundedAction a) {
		return this.getQ(this.stateHash(s), a);
	}
	
	

	protected List<QValue> getQs(StateHashTuple s) {
		QLearningStateNode node = this.getStateNode(s);
		return node.qEntry;
	}


	protected QValue getQ(StateHashTuple s, GroundedAction a) {
		QLearningStateNode node = this.getStateNode(s);
		
		if(a.params.length > 0){
			Map<String, String> matching = s.s.getExactStateObjectMatchingTo(node.s.s);
			a = this.translateAction(a, matching);
		}
		
		for(QValue qv : node.qEntry){
			if(qv.a.equals(a)){
				return qv;
			}
		}
		
		return null; //no action for this state indexed / raise problem
	}
	
	protected QLearningStateNode getStateNode(StateHashTuple s){
		
		QLearningStateNode node = qIndex.get(s);
		
		if(node == null){
			node = new QLearningStateNode(s);
			List<GroundedAction> gas = this.getAllGroundedActions(s.s);
			for(GroundedAction ga : gas){
				node.addQValue(ga, qInit);
			}
			
			qIndex.put(s, node);
		}
		
		return node;
		
	}
	
	protected double getMaxQ(StateHashTuple s){
		List <QValue> qs = this.getQs(s);
		double max = Double.NEGATIVE_INFINITY;
		for(QValue q : qs){
			if(q.q > max){
				max = q.q;
			}
		}
		return max;
	}

	@Override
	public void planFromState(State initialState) {
		
		int eCount = 0;
		do{
			this.runLearningEpisodeFrom(initialState);
			eCount++;
		}while(eCount < numEpisodesForPlanning && maxQChangeInLastEpisode > maxQChangeForPlanningTermination);
		

	}


	@Override
	public EpisodeAnalysis runLearningEpisodeFrom(State initialState) {
		
		EpisodeAnalysis ea = new EpisodeAnalysis(initialState);
		
		StateHashTuple curState = this.stateHash(initialState);
		eStepCounter = 0;
		
		maxQChangeInLastEpisode = 0.;
		
		while(!tf.isTerminal(curState.s) && eStepCounter < maxEpisodeSize){
			
			GroundedAction action = learningPolicy.getAction(curState.s);
			QValue curQ = this.getQ(curState, action);
			
			StateHashTuple nextState = this.stateHash(action.executeIn(curState.s));
			double maxQ = 0.;
			
			if(!tf.isTerminal(nextState.s)){
				maxQ = this.getMaxQ(nextState);
			}
			
			//manage option specifics
			double r = 0.;
			double discount = this.gamma;
			if(action.action.isPrimitive()){
				r = rf.reward(curState.s, action, nextState.s);
				eStepCounter++;
			}
			else{
				Option o = (Option)action.action;
				r = o.getLastCumulativeReward();
				int n = o.getLastNumSteps();
				discount = Math.pow(this.gamma, n);
				eStepCounter += n;
			}
			
			ea.recordTransitionTo(nextState.s, action, r);
			
			double oldQ = curQ.q;
			
			//update Q-value
			curQ.q = curQ.q + this.learningRate * (r + (discount * maxQ) - curQ.q);
			
			double deltaQ = Math.abs(oldQ - curQ.q);
			if(deltaQ > maxQChangeInLastEpisode){
				maxQChangeInLastEpisode = deltaQ;
			}
			
			//move on
			curState = nextState;
			
			
		}
		
		if(episodeHistory.size() >= numEpisodesToStore){
			episodeHistory.poll();
			episodeHistory.offer(ea);
		}
		
		return ea;
	}


	@Override
	public EpisodeAnalysis getLastLearningEpisode() {
		return episodeHistory.getLast();
	}


	@Override
	public void setNumEpisodesToStore(int numEps) {
		if(numEps > 0){
			numEpisodesToStore = numEps;
		}
		else{
			numEpisodesToStore = 1;
		}
	}


	@Override
	public List<EpisodeAnalysis> getAllStoredLearningEpisodes() {
		return episodeHistory;
	}

}
