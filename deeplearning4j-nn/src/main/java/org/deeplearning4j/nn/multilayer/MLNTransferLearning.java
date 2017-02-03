package org.deeplearning4j.nn.multilayer;

import org.deeplearning4j.nn.conf.BackpropType;
import org.deeplearning4j.nn.conf.InputPreProcessor;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.inputs.InputType;
import org.deeplearning4j.nn.conf.layers.Layer;
import org.deeplearning4j.nn.weights.WeightInit;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.indexing.NDArrayIndex;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * The API usage(WIP):
 *
 *  MLNTransferLearning.Builder(MultiLayerNetwork origModel)
     .setFeatureExtractor(int layerNum)
            //which layerNum and below to freeze, probably better to take a negative number so you can say "freeze everything but the last n layers"
     .finetuneConfiguration(NeuralNetConfiguration.Builder newDefaultConfBuilder)
            //same as the global config in neuralnetconfiguration - will override whatever is in the origModel's config
     .nOutReplace(int layerNum, int nOut, WeightInit scheme)
            //helper to tweak nOut of a given layer
     .nInReplace(int layerNum, WeightInit scheme)
            //helper to set weight init scheme for the next layer
     .popFromLayer(int layerNum)
            //will delete all layers from this to output
     .addLayer(Layer layer)
            // each layer must be explicitly added and will use the "global config" from finetuneConfigu..
     .build()
            //will return a MLN, init with a view of the olderParams+whatever newer params
 Other things to consider:
 - There really should be a way to featurize and save to disk and then train from the featurized data. This will help users iterate quicker and
 get a "nano net" that converges fast and then they can "fineTune" to their heart's content without wondering about having disruptive gradients
 flowing backward to the unfrozen layers.
 - And then adapting this for computation graphs (yikes)
 - Also a summary of the model before and after to show how many new params were added/deleted and how many are learnable and how many are frozen etc..
 */
public class MLNTransferLearning {

    private MultiLayerConfiguration origConf;
    private INDArray origParams;

    public static class Builder {

        private INDArray origParams;
        private int tillIndex;
        private List<INDArray> appendParams; //these could be new arrays, and views from origParams
        private MultiLayerConfiguration origConf;
        private MultiLayerNetwork origModel;
        private MultiLayerNetwork editedModel;
        private NeuralNetConfiguration.Builder globalConfig;

        private int frozenTill = 0;

        protected List<NeuralNetConfiguration> confs = new ArrayList<>();
        private List<NeuralNetConfiguration> editedConfs = new ArrayList<>();
        protected Map<Integer,InputPreProcessor> inputPreProcessors = new HashMap<>();
        protected boolean pretrain = false;
        protected boolean backprop = true;
        protected BackpropType backpropType = BackpropType.Standard;
        protected int tbpttFwdLength = 20;
        protected int tbpttBackLength = 20;
        protected InputType inputType;

        private Builder(MultiLayerConfiguration origConf) {
            this.origConf = origConf;
            this.confs = origConf.getConfs();
            this.inputPreProcessors = origConf.getInputPreProcessors();
            this.pretrain = origConf.isPretrain();
            this.backprop = origConf.isBackprop();
            this.backpropType = origConf.getBackpropType();
            this.tbpttFwdLength = origConf.getTbpttFwdLength();
            this.tbpttBackLength = origConf.getTbpttBackLength();
            this.inputType = origConf.getInputType(); //FIXME:no getter
        }

        public Builder(MultiLayerNetwork origModel) {
            this(origModel.getLayerWiseConfigurations());
            this.origParams  = origModel.params();
            this.origModel = origModel;
        }

        public Builder setFeatureExtractor(int layerNum) {
            this.frozenTill = layerNum;
            return this;
        }

        public Builder finetuneConfiguration(NeuralNetConfiguration.Builder newDefaultConfBuilder) {
            this.globalConfig = newDefaultConfBuilder;
            org.deeplearning4j.nn.api.Layer [] layers = origModel.getLayers();
            for (int i=0;i<layers.length;i++) {
                NeuralNetConfiguration layerConf = origModel.getLayerWiseConfigurations().getConf(i);
                Layer layerImpl = layerConf.getLayer();
                // for_every_param
                //layerConf.setLearningRateByParam();
                //FIXME - grab each of these and override in the layerImpl like this or even better clear these from the layerImpl and then??
                layerImpl.setUpdater(??);
                layerImpl.setMomentum(??);
                //FIXME - instantiate a dummy layer and then grab config and set in the editedConf list??
                INDArray dummyParams = Nd4j.create(1,layerImpl.initializer().numParams(layerConf);
                org.deeplearning4j.nn.api.Layer dummyLayer = layerImpl.instantiate(layerConf,null,0,dummyParams,true);
                editedConf.set(i,dummyLayer.conf());
            }
            return this;
        }

        public Builder nOutReplace(int layerNum, int nOut, WeightInit scheme) {
            INDArray appendParams;
            org.deeplearning4j.nn.api.Layer [] layers = origModel.getLayers();
            int paramCount = 0;
            for (int i=0;i<layers.length;i++) {
                if (i == layerNum) {
                    NeuralNetConfiguration layerConf = origModel.getLayerWiseConfigurations().getConf(i);
                    Layer layerImpl = layerConf.getLayer();
                    //FIXME - layerConf gets edited for nIn and nOut setters, keep it as package private
                    layerImpl.setnIn(..);
                    layerImpl.setnOut(nOut);
                    layerImpl.setWeightInit(scheme);
                    int numParams = layerImpl.initializer().numParams(layerConf);
                    INDArray params = Nd4j.create(1, numParams);
                    org.deeplearning4j.nn.api.Layer someLayer = layerImpl.instantiate(layerConf,null,0,params,true);
                    appendParams.add(params); //FIXLATER:does this need to be a list or something

                }
                else if (i < layerNum){
                    paramCount += origModel.getLayer(i).params().length();
                }
            }
            return this;
        }

        public Builder nInReplace(int layerNum, WeightInit scheme) {
            //same idea as above - the editConfs get updated and the appendParams get updated
            return this;
        }


        public Builder popFromLayer(int layerNum) {
            for (int i=layerNum;i<origConf.getConfs().size();i++) {
                editedConfs.remove(i);
            }
            return this;
        }

        public Builder addLayer(Layer layer) {
            // Use the fineTune NeuralNetConfigurationBuilder and the layerConf to get a N
            editedConfs.add(globalConfig.clone().layer(layer).build());
            //instantiate dummy layer
            org.deeplearning4j.nn.api.Layer dummyLayer = editedConfs.get(editedConfs.size()-1).getLayer().instantiate(globalConfig.build(),....)
            appendParams.add(dummyLayer.params());
            return this;
        }

        public MultiLayerNetwork build() {
            if (editConf() == null) {
                editedModel = origModel;
            }
            else {
                editedModel = new MultiLayerNetwork(editConf(),constructParams());
            }
            //editedModel.setParams(constructParams());
            org.deeplearning4j.nn.api.Layer [] layers = editedModel.getLayers();
            for (int i=frozenTill;i>=0;i--) {
                layers[i] = new FrozenLayer(layers[i]);
            }
            editedModel.setLayers(layers);
            return editedModel;
        }

        private INDArray constructParams() {
            INDArray keepView = origParams.get(NDArrayIndex.point(0),NDArrayIndex.interval(0,tillIndex,true));
            INDArray appendView = Nd4j.hstack(appendParams);
            return Nd4j.hstack(keepView,appendView);
        }

        private MultiLayerConfiguration editConf() {
            //use the editedConfs list to make a new config
            return new MultiLayerConfiguration.Builder().backprop(backprop).inputPreProcessors(inputPreProcessors).
                    pretrain(pretrain).backpropType(backpropType).tBPTTForwardLength(tbpttFwdLength)
                    .tBPTTBackwardLength(tbpttBackLength)
                    .setInputType(this.inputType)
                    .confs(editedConfs).build();
        }
    }
}