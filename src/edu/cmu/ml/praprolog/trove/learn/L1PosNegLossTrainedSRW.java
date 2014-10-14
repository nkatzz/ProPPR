package edu.cmu.ml.praprolog.trove.learn;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.apache.log4j.Logger;

import edu.cmu.ml.praprolog.learn.tools.LossData;
import edu.cmu.ml.praprolog.learn.tools.WeightingScheme;
import edu.cmu.ml.praprolog.learn.tools.LossData.LOSS;
import edu.cmu.ml.praprolog.trove.learn.tools.PosNegRWExample;
import edu.cmu.ml.praprolog.util.Dictionary;
import edu.cmu.ml.praprolog.util.ParamVector;
import gnu.trove.map.TIntDoubleMap;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntDoubleHashMap;
import gnu.trove.map.hash.TObjectDoubleHashMap;

public class L1PosNegLossTrainedSRW extends SRW<PosNegRWExample> {
	private static final Logger log = Logger.getLogger(L1PosNegLossTrainedSRW.class);
	private static final double bound = 1.0e-15; //Prevent infinite log loss.

	protected LossData cumloss;
	
	
	public L1PosNegLossTrainedSRW(int maxT, double mu, double eta, WeightingScheme wScheme, double delta, File affgraph, double zeta) {
		super(maxT,mu,eta,wScheme,delta,affgraph,zeta);
		this.cumloss = new LossData();
	}

	public L1PosNegLossTrainedSRW() {
		super();
		this.cumloss = new LossData();
	}

	/**
	 * Compute the local gradient of the parameters, associated
        with a particular start vector and pos/neg examples.
        
        loss function is F(w) = <regularization> + sum_{x in pos}[ -ln p[x]] + sum_{x in neg}[ -ln 1-p[x]]
        d/df F(w) = <regularization> + sum_{x in pos}[ - 1/p[x] * d/df p[x] ] + sum_{x in neg}[ - 1/(1-p[x]) * d/df p[x] ]
	 */
	public TObjectDoubleHashMap<String> gradient(ParamVector paramVec, PosNegRWExample example) {
		
		// compute regularization
		TObjectDoubleHashMap<String> derivFparamVec = new TObjectDoubleHashMap<String>();
		for (String f : localFeatures(paramVec,example)) {
			derivFparamVec.put(f,derivRegularization(f,paramVec));
		}
		
		// compute p
		TIntDoubleMap p = rwrUsingFeatures(example.getGraph(), example.getQueryVec(), paramVec);
		TIntObjectMap<TObjectDoubleHashMap<String>> d = derivRWRbyParams(example.getGraph(), example.getQueryVec(), paramVec);
		
		Set<String> trainableFeatures = trainableFeatures(localFeatures(paramVec,example)); 
		
		//compute gradient
		double pmax = 0;

		for (int x : example.getPosList()) {
			TObjectDoubleHashMap<String> dx = d.get(x);
			double px = p.get(x);
			if(px > pmax) pmax = px;
			for (String f : trainableFeatures) {
				if (Dictionary.safeContains(d,x,f)) {
					Dictionary.increment(derivFparamVec, f, -dx.get(f)/px);
				}
			}
			this.cumloss.add(LOSS.LOG, -Math.log(checkProb(px)));
		}

		//negative instance booster
    	double h = pmax + delta;
		double beta = 1;
		if(delta < 0.5) beta = (Math.log(1/h))/(Math.log(1/(1-h)));
		
		for (int x : example.getNegList()) {
			TObjectDoubleHashMap<String> dx = d.get(x);
			double px = p.get(x);
			for (String f : trainableFeatures) {
				if (Dictionary.safeContains(d,x,f)) 
					Dictionary.increment(derivFparamVec, f, beta*dx.get(f)/(1-px));
			}
			this.cumloss.add(LOSS.LOG, -Math.log(checkProb(1.0-px)));
		}
		return derivFparamVec;
	}

	/**
	 * [Originally from L1RegularizedLearner; Java doesn't do multiple inheritance]
	 * @param f
	 * @param paramVec
	 * @return
	 */
	protected Double derivRegularization(String f, ParamVector paramVec) {
		double value = Dictionary.safeGet(paramVec, f);
		double ret = untrainedFeatures.contains(f) ? 0.0 : mu;
		this.cumloss.add(LOSS.REGULARIZATION, this.mu);
		return ret;
	}

	public double checkProb(double prob)
	{
		if(prob <= 0)
		{
			prob = bound;
		}
		return prob;
	}

	@Override
	public LossData cumulativeLoss() {
		return cumloss.copy();
	}
	@Override
	public void clearLoss() {
		cumloss.clear(); // ?
	}
}
