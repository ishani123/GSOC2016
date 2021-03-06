package org.gsoc.siddhi.extension.streaming;

import org.wso2.siddhi.core.config.ExecutionPlanContext;
import org.wso2.siddhi.core.event.ComplexEvent;
import org.wso2.siddhi.core.event.ComplexEventChunk;
import org.wso2.siddhi.core.event.stream.StreamEvent;
import org.wso2.siddhi.core.event.stream.StreamEventCloner;
import org.wso2.siddhi.core.event.stream.populater.ComplexEventPopulater;
import org.wso2.siddhi.core.exception.ExecutionPlanCreationException;
import org.wso2.siddhi.core.executor.ConstantExpressionExecutor;
import org.wso2.siddhi.core.executor.ExpressionExecutor;
import org.wso2.siddhi.core.query.processor.Processor;
import org.wso2.siddhi.core.query.processor.stream.StreamProcessor;
import org.wso2.siddhi.query.api.definition.AbstractDefinition;
import org.wso2.siddhi.query.api.definition.Attribute;

import java.util.ArrayList;
import java.util.List;
/**
 * Created by mahesh on 6/4/16.
 */
public class StreamingKMeansClusteringStreamProcessor extends StreamProcessor {

    private int learnType =0;
    private int paramCount = 0;                                         // Number of x variables +1
    private int batchSize = 10;                                          // Maximum # of events, used for regression calculation
    private double ci = 0.95;                                           // Confidence Interval

    private int k;
    private int numClusters;
    private int numIterations;
    private double alpha=0;
    private int paramPosition = 0;
    private int featureSize=1;  //P
    private StreamingKMeansClustering streamingKMeansClustering = null;

    @Override
    protected List<Attribute> init(AbstractDefinition inputDefinition, ExpressionExecutor[] attributeExpressionExecutors, ExecutionPlanContext executionPlanContext) {
        paramCount = attributeExpressionLength;
        int PARAM_WIDTH=6;
        // Capture constant inputs
        if (attributeExpressionExecutors[0] instanceof ConstantExpressionExecutor) {

            paramCount = paramCount - PARAM_WIDTH;
            featureSize=paramCount;//

            paramPosition = PARAM_WIDTH;
            try {
                learnType = ((Integer) attributeExpressionExecutors[0].execute(null));
                batchSize = ((Integer) attributeExpressionExecutors[1].execute(null));
                numClusters = ((Integer) attributeExpressionExecutors[2].execute(null));
                numIterations = ((Integer) attributeExpressionExecutors[3].execute(null));
                alpha         = ((Integer) attributeExpressionExecutors[4].execute(null));
            } catch (ClassCastException c) {
                throw new ExecutionPlanCreationException("Calculation interval, batch size and range should be of type int");
            }
            try {
                ci = ((Double) attributeExpressionExecutors[5].execute(null));
            } catch (ClassCastException c) {
                throw new ExecutionPlanCreationException("Confidence interval should be of type double and a value between 0 and 1");
            }
        }
        System.out.println("Parameters: "+" "+batchSize+" "+" "+ci+"\n");
        // Pick the appropriate regression calculator

        streamingKMeansClustering = new StreamingKMeansClustering(0,paramCount, batchSize, ci,numClusters, numIterations,alpha);

        // Add attributes for standard error and all beta values
        String betaVal;
        ArrayList<Attribute> attributes = new ArrayList<Attribute>(paramCount);
        attributes.add(new Attribute("stderr", Attribute.Type.DOUBLE));

        for (int itr = 0; itr < paramCount; itr++) {
            betaVal = "beta" + itr;
            attributes.add(new Attribute(betaVal, Attribute.Type.DOUBLE));
        }

        return attributes;
    }

    @Override
    protected void process(ComplexEventChunk<StreamEvent> streamEventChunk, Processor nextProcessor, StreamEventCloner streamEventCloner, ComplexEventPopulater complexEventPopulater) {
        synchronized (this) {
            while (streamEventChunk.hasNext()) {
                ComplexEvent complexEvent = streamEventChunk.next();

                Object[] inputData = new Object[attributeExpressionLength - paramPosition];
                Double[] eventData = new Double[attributeExpressionLength - paramPosition];


                for (int i = paramPosition; i < attributeExpressionLength; i++) {
                    inputData[i - paramPosition] = attributeExpressionExecutors[i].execute(complexEvent);
                    eventData[i - paramPosition] = (Double) attributeExpressionExecutors[i].execute(complexEvent);
                }

                //Object[] outputData = regressionCalculator.calculateLinearRegression(inputData);
                Object[] outputData = null;

                // Object[] outputData= streamingLinearRegression.addToRDD(eventData);
                //Calling the regress function
                outputData = streamingKMeansClustering.cluster(eventData);
                if((Double)outputData[0]==0.0){
                    outputData=null;
                }
                System.out.println("OutputData: "+outputData);

                // Skip processing if user has specified calculation interval
                if (outputData == null) {
                    streamEventChunk.remove();
                } else {
                    complexEventPopulater.populateComplexEvent(complexEvent, outputData);
                }
            }
        }
        nextProcessor.process(streamEventChunk);

    }

    @Override
    public void start() {

    }

    @Override
    public void stop() {

    }

    @Override
    public Object[] currentState() {
        return new Object[0];
    }

    @Override
    public void restoreState(Object[] state) {

    }

}
