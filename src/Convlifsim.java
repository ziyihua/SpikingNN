import org.math.plot.utils.Array;

import java.util.ArrayList;
import java.util.Random;

/**
 * Created by ziyihua on 18/07/15.
 */
public class Convlifsim extends Structure {
    public Convlifsim(){
    }


    public static network Convlifsim(network convnet, double[][][] test_x, int[][] test_y_new, int[] test_y, double t_ref, double threshold, double dt, double duration, double report_every, double max_rate){

        int num_examples = test_x[0][0].length;
        int num_classes = test_y_new.length;

        Random r = new Random();

        float[] accuracy = new float[(int)(duration/dt)+1];
        int acc_indx = 0;

        //initialization
        for (int i = 0; i < convnet.layers.size(); i++) {

            int outputmaps = convnet.layers.get(i).a.get(0).a_list.size();
            for (int j = 0; j < outputmaps; j++) {
                float[][][] correctly_sized_zeros = new float[((double[][][])convnet.layers.get(i).a.get(0).a_list.get(j)).length][((double[][][])convnet.layers.get(i).a.get(0).a_list.get(j))[0].length][num_examples];
                for (int k = 0; k < correctly_sized_zeros.length; k++) {
                    for (int l = 0; l < correctly_sized_zeros[0].length; l++) {
                        for (int m = 0; m < correctly_sized_zeros[0][0].length; m++) {
                            correctly_sized_zeros[k][l][m]=0.0f;
                        }
                    }
                }
                convnet.layers.get(i).m.add(j,correctly_sized_zeros);
                convnet.layers.get(i).r.add(j,correctly_sized_zeros);
                convnet.layers.get(i).s.add(j,correctly_sized_zeros);
                convnet.layers.get(i).sp.add(j,correctly_sized_zeros);
            }
        }

        convnet.sum_fv = new float[convnet.ffW[0].length][num_examples];
        for (int i = 0; i < convnet.ffW[0].length; i++) {
            for (int j = 0; j < num_examples; j++) {
                convnet.sum_fv[i][j]=0.0f;
            }
        }

        convnet.o_mem = new float[num_classes][num_examples];
        for (int i = 0; i < convnet.o_mem.length; i++) {
            for (int j = 0; j < convnet.o_mem[0].length; j++) {
                convnet.o_mem[i][j]=0.0f;
            }
        }

        convnet.o_refrac_end = new float[num_classes][num_examples];
        for (int i = 0; i < convnet.o_refrac_end.length; i++) {
            for (int j = 0; j < convnet.o_refrac_end[0].length; j++) {
                convnet.o_refrac_end[i][j]=0.0f;
            }
        }

        convnet.o_sum_spikes = new float[num_classes][num_examples];
        for (int i = 0; i < convnet.o_sum_spikes.length; i++) {
            for (int j = 0; j < convnet.o_sum_spikes[0].length; j++) {
                convnet.o_sum_spikes[i][j]=0.0f;
            }
        }


        for (double i = 0; i <= duration; i=i+dt) {
            //create Poisson distributed spikes form the input images (for all images in parallel)
            double rescale_fac = 1/(dt*max_rate);
            float[][][] spike_snapshot = new float[test_x.length][test_x[0].length][test_x[0][0].length];
            for (int j = 0; j < spike_snapshot.length; j++) {
                for (int k = 0; k < spike_snapshot[0].length; k++) {
                    for (int l = 0; l < spike_snapshot[0][0].length; l++) {
                        double RandomValue = r.nextDouble();
                        spike_snapshot[j][k][l]= (float)(RandomValue * rescale_fac);
                    }
                }
            }
            float[][][] inp_image = new float[test_x.length][test_x[0].length][test_x[0][0].length];
            for (int j = 0; j < inp_image.length; j++) {
                for (int k = 0; k < inp_image[0].length; k++) {
                    for (int l = 0; l < inp_image[0][0].length; l++) {
                        if (spike_snapshot[j][k][l] <= test_x[j][k][l])
                            inp_image[j][k][l] = 1.0f;
                        else inp_image[j][k][l] = 0.0f;
                    }
                }
            }


            if(convnet.layers.get(0).sp.isEmpty()){
                convnet.layers.get(0).sp.add(0,inp_image);
            }else convnet.layers.get(0).sp.set(0,inp_image);


            float[][][] mem_i_m = new float[inp_image.length][inp_image[0].length][inp_image[0][0].length];
            for (int j = 0; j < mem_i_m.length; j++) {
                for (int k = 0; k < mem_i_m[0].length; k++) {
                    for (int l = 0; l < mem_i_m[0][0].length; l++) {
                        mem_i_m[j][k][l]=convnet.layers.get(0).m.get(0)[j][k][l]+inp_image[j][k][l];
                    }
                }
            }
            convnet.layers.get(0).m.set(0,mem_i_m);


            float[][][] sum_spikes_i_m = new float[inp_image.length][inp_image[0].length][inp_image[0][0].length];
            for (int j = 0; j < inp_image.length; j++) {
                for (int k = 0; k < inp_image[0].length; k++) {
                    for (int l = 0; l < inp_image[0][0].length; l++) {
                        sum_spikes_i_m[j][k][l]=convnet.layers.get(0).s.get(0)[j][k][l]+inp_image[j][k][l];
                    }
                }
            }
            convnet.layers.get(0).s.set(0,sum_spikes_i_m);

            int inputmaps = 1;


            for (int j = 1; j < convnet.layers.size(); j++) {

                if ("c".equals(convnet.layers.get(j).type)) {

                    int a = convnet.layers.get(j - 1).s.get(0).length;
                    int b = convnet.layers.get(j - 1).s.get(0)[0].length;
                    int c = convnet.layers.get(j - 1).s.get(0)[0][0].length;
                    int a_new = a - convnet.layers.get(j).kernelsize + 1;
                    int b_new = b - convnet.layers.get(j).kernelsize + 1;


                    //output a map for each convolution
                    for (int o = 0; o < convnet.layers.get(j).outmaps; o++) {

                        //sum up input maps
                        float[][][] z = new float[a_new][b_new][c];
                        for (int k = 0; k < a_new; k++) {
                            for (int l = 0; l < b_new; l++) {
                                for (int m = 0; m < c; m++) {
                                    z[k][l][m] = 0.0f;
                                }
                            }
                        }

                        for (int k = 0; k < inputmaps; k++) {
                            //for each input map convolve with corresponding kernel and add to temp output map
                            //a matrix-wise 2D convolution along the 3rd dimension is used instead of 3D convolution
                            for (int l = 0; l < c; l++) {
                                double[][] s_one = new double[a][b];
                                for (int m = 0; m < a; m++) {
                                    for (int n = 0; n < b; n++) {
                                        s_one[m][n] = convnet.layers.get(j - 1).s.get(k)[m][n][l];
                                    }
                                }
                                double[][] z_conv = Convolution.convolution2D(s_one, s_one.length, s_one[0].length, ((double[][]) convnet.layers.get(j).k.get(0).k_list.get(k * convnet.layers.get(j).outmaps + o)), convnet.layers.get(j).kernelsize, convnet.layers.get(j).kernelsize);
                                for (int m = 0; m < z.length; m++) {
                                    for (int n = 0; n < z[0].length; n++) {
                                        z[m][n][l] = z[m][n][l] + (float) z_conv[m][n];
                                    }
                                }
                            }
                        }

                        //only allow non-refractory neurons to get input
                        for (int l = 0; l < z.length; l++) {
                            for (int m = 0; m < z[0].length; m++) {
                                for (int n = 0; n < z[0][0].length; n++) {
                                    if (convnet.layers.get(j).r.get(o)[l][m][n] > i) {
                                        z[l][m][n] = 0.0f;
                                    }
                                }
                            }
                        }

                        //add input
                       float[][][] mem_c_m = new float[z.length][z[0].length][z[0][0].length];
                        for (int l = 0; l < z.length; l++) {
                            for (int m = 0; m < z[0].length; m++) {
                                for (int n = 0; n < z[0][0].length; n++) {
                                    mem_c_m[l][m][n] = convnet.layers.get(j).m.get(o)[l][m][n] + z[m][l][n];
                                }
                            }
                        }

                        //check for spiking
                        float[][][] spikes_c_m = new float[z.length][z[0].length][z[0][0].length];
                        for (int k = 0; k < z.length; k++) {
                            for (int l = 0; l < z[0].length; l++) {
                                for (int m = 0; m < z[0][0].length; m++) {
                                    if (mem_c_m[k][l][m] >= threshold) {
                                        spikes_c_m[k][l][m] = 1.0f;
                                    } else {
                                        spikes_c_m[k][l][m] = 0.0f;
                                    }
                                }
                            }
                        }

                        //reset
                        for (int k = 0; k < z.length; k++) {
                            for (int l = 0; l < z[0].length; l++) {
                                for (int m = 0; m < z[0][0].length; m++) {
                                    if (spikes_c_m[k][l][m] == 1.0f)
                                        mem_c_m[k][l][m] = 0.0f;
                                }
                            }
                        }

                        //ban updates until...
                        float[][][] refrac_end_c_m = new float[z.length][z[0].length][z[0][0].length];
                        for (int k = 0; k < z.length; k++) {
                            for (int l = 0; l < z[0].length; l++) {
                                for (int m = 0; m < z[0][0].length; m++) {
                                    if (spikes_c_m[k][l][m] == 1.0) {
                                        refrac_end_c_m[k][l][m] = (float)(i + t_ref);
                                    } else {
                                        refrac_end_c_m[k][l][m] = convnet.layers.get(j).r.get(o)[k][l][m];
                                    }
                                }
                            }
                        }

                        //store results for analysis later
                        float[][][] sum_spikes_c_m = new float[z.length][z[0].length][z[0][0].length];
                        for (int k = 0; k < z.length; k++) {
                            for (int l = 0; l < z[0].length; l++) {
                                for (int m = 0; m < z[0][0].length; m++) {
                                    sum_spikes_c_m[k][l][m] = convnet.layers.get(j).s.get(o)[k][l][m] + spikes_c_m[k][l][m];
                                }
                            }
                        }


                        convnet.layers.get(j).m.set(o, mem_c_m);
                        convnet.layers.get(j).sp.set(o, spikes_c_m);
                        convnet.layers.get(j).r.set(o, refrac_end_c_m);
                        convnet.layers.get(j).s.set(o, sum_spikes_c_m);

                    }
                    inputmaps = convnet.layers.get(j).outmaps;
                }

                if ("s".equals(convnet.layers.get(j).type)){

                    //subsample by averaging
                    LAYER layer_previous = convnet.layers.get(j-1);
                    LAYER layer_current = convnet.layers.get(j);
                    int a = layer_previous.s.get(0).length;
                    int b = layer_previous.s.get(0)[0].length;
                    int c = layer_previous.s.get(0)[0][0].length;
                    int a_new = a-layer_current.scale+1;
                    int b_new = b-layer_current.scale+1;
                    double[][] subsample = new double[layer_current.scale][layer_current.scale];
                    for (int k = 0; k < layer_current.scale; k++) {
                        for (int l = 0; l < layer_current.scale; l++) {
                            subsample[k][l]=(double) 1/(layer_current.scale*layer_current.scale);
                        }
                    }

                    for (int k = 0; k < inputmaps; k++) {
                        float[][][] z = new float[a_new][b_new][c];
                        //matrix-wise 2D convolution
                        for (int l = 0; l < c; l++) {
                            double[][] s_one = new double[a][b];
                            for (int m = 0; m < a; m++) {
                                for (int n = 0; n < b; n++) {
                                    s_one[m][n]= layer_previous.s.get(k)[m][n][l];
                                }
                            }
                            double[][] z_conv = Convolution.convolution2D(s_one,a,b,subsample,layer_current.scale,layer_current.scale);
                            for (int m = 0; m < a_new; m++) {
                                for (int n = 0; n < b_new; n++) {
                                    z[m][n][l]=(float)z_conv[m][n];
                                }
                            }
                        }
                        //downsample
                        float[][][] m = new float[a/layer_current.scale][b/layer_current.scale][c];
                        for (int l = 0; l < a/layer_current.scale; l++) {
                            for (int n = 0; n < b/layer_current.scale; n++) {
                                for (int o = 0; o < c; o++) {
                                    m[l][n][o]=z[l*2][n*2][o];
                                }
                            }
                        }

                        //only allow non-refractory neurons to get input
                        for (int l = 0; l < m.length; l++) {
                            for (int n = 0; n < m[0].length; n++) {
                                for (int o = 0; o < m[0][0].length; o++) {
                                    if (convnet.layers.get(j).r.get(k)[l][n][o]>i){
                                        m[l][n][o]=0.0f;
                                    }
                                }
                            }
                        }

                        //add input
                        float[][][] mem_s_m = new float[m.length][m[0].length][m[0][0].length];
                        for (int l = 0; l < m.length; l++) {
                            for (int n = 0; n < m[0].length; n++) {
                                for (int o = 0; o < m[0][0].length; o++) {
                                    mem_s_m[l][n][o]=convnet.layers.get(j).m.get(k)[l][n][o]+m[l][n][o];
                                }
                            }
                        }

                        //check for spiking
                        float[][][] spikes_s_m = new float[m.length][m[0].length][m[0][0].length];
                        for (int l = 0; l < m.length; l++) {
                            for (int n = 0; n < m[0].length; n++) {
                                for (int o = 0; o < m[0][0].length; o++) {
                                    if (mem_s_m[l][n][o]>=threshold){
                                        spikes_s_m[l][n][o]=1.0f;
                                    }else{
                                        spikes_s_m[l][n][o]=0.0f;
                                    }
                                }
                            }
                        }

                        //reset
                        for (int l = 0; l < m.length; l++) {
                            for (int n = 0; n < m[0].length; n++) {
                                for (int o = 0; o < m[0][0].length; o++) {
                                    if (spikes_s_m[l][n][o]==1.0){
                                        mem_s_m[l][n][o]=0.0f;
                                    }
                                }
                            }
                        }

                        //ban updates until...
                        float[][][] refrac_end_s_m = new float[m.length][m[0].length][m[0][0].length];
                        for (int l = 0; l < m.length; l++) {
                            for (int n = 0; n < m[0].length; n++) {
                                for (int o = 0; o < m[0].length; o++) {
                                    if (spikes_s_m[l][n][o]==1.0){
                                        refrac_end_s_m[l][n][o]=(float)(i+t_ref);
                                    }else {
                                        refrac_end_s_m[l][n][o]= layer_current.r.get(k)[l][n][o];
                                    }
                                }
                            }
                        }

                        //store results for analysis later
                        float[][][] sum_spikes_s_m = new float[m.length][m[0].length][m[0][0].length];
                        for (int l = 0; l < m.length; l++) {
                            for (int n = 0; n < m[0].length; n++) {
                                for (int o = 0; o < m[0][0].length; o++) {
                                    sum_spikes_s_m[l][n][o]=layer_current.s.get(k)[l][n][o]+spikes_s_m[l][n][o];
                                }
                            }
                        }

                        layer_current.m.set(k,mem_s_m);
                        layer_current.sp.set(k,spikes_s_m);
                        layer_current.s.set(k,sum_spikes_s_m);
                        layer_current.r.set(k,refrac_end_s_m);

                    }
                }
            }

            //concatenate all end layer feature maps into vector
            LAYER layer_current = convnet.layers.get(convnet.layers.size()-1);
            int a = layer_current.s.get(0).length;
            int b = layer_current.s.get(0)[0].length;
            int c = layer_current.s.get(0)[0][0].length;
            int outputmaps = layer_current.s.size();

            //fv
            double[][] fv = new double[a*b*outputmaps][c];
            for (int j = 0; j < c; j++) {
                int row = 0;
                for (int k = 0; k < outputmaps; k++) {
                    for (int l = 0; l < b; l++) {
                        for (int m = 0; m < a; m++) {
                            fv[row][j]=layer_current.s.get(k)[m][l][j];
                            row++;
                        }
                    }
                }
            }
            convnet.fv = fv;

            //sum_fv
            convnet.sum_fv = new float[fv.length][fv[0].length];
            for (int j = 0; j < fv.length; j++) {
                for (int k = 0; k < fv[0].length; k++) {
                    convnet.sum_fv[j][k] = convnet.sum_fv[j][k]+(float)fv[j][k];
                }
            }


            //run the output layer neurons
            //add inputs multiplied by weights
            //ffw*fv
            int d = convnet.ffW.length;
            int e = convnet.fv.length;
            float[][] impulse = new float[d][c];
            for (int j = 0; j < d; j++) {
                for (int k = 0; k < c; k++) {
                    impulse[j][k]=0.0f;
                }
            }
            for (int j = 0; j < d; j++) {
                for (int k = 0; k < c; k++) {
                    for (int l = 0; l < e; l++) {
                        impulse[j][k]=impulse[j][k]+(float)(convnet.ffW[j][l]*convnet.fv[l][k]);
                    }
                }
            }

            //only add input from neurons past their refractory point
            for (int j = 0; j < d; j++) {
                for (int k = 0; k < c; k++) {
                    if (convnet.o_refrac_end[j][k]>=i){
                        impulse[j][k]=0.0f;
                    }
                }
            }

            //add input to membrane potential
            for (int j = 0; j < d; j++) {
                for (int k = 0; k < c; k++) {
                    convnet.o_mem[j][k]=convnet.o_mem[j][k]+impulse[j][k];
                }
            }


            //check for spiking
            convnet.o_spikes = new float[d][c];
            for (int j = 0; j < d; j++) {
                for (int k = 0; k < c; k++) {
                    if (convnet.o_mem[j][k]>=threshold){
                        convnet.o_spikes[j][k]=1.0f;
                    }
                    else {
                        convnet.o_spikes[j][k]=0.0f;
                    }
                }
            }

            //reset
            for (int j = 0; j < d; j++) {
                for (int k = 0; k < c; k++) {
                    if (convnet.o_spikes[j][k]==1.0){
                        convnet.o_mem[j][k]=0.0f;
                    }
                }
            }

            //ban updates until...
            for (int j = 0; j < d; j++) {
                for (int k = 0; k < c; k++) {
                    if (convnet.o_spikes[j][k]==1.0){
                        convnet.o_refrac_end[j][k]=(float)(i+t_ref);
                    }else {
                        convnet.o_refrac_end[j][k]=convnet.o_refrac_end[j][k];
                    }

                }
            }

            //store results for analysis later
            convnet.o_sum_spikes = new float[d][c];
            for (int j = 0; j < d; j++) {
                for (int k = 0; k < c; k++) {
                    convnet.o_sum_spikes[j][k] = convnet.o_sum_spikes[j][k]+convnet.o_spikes[j][k];
                }
            }

            //tell the user what is going on
            if ((((Math.round(i/dt)) % (Math.round(report_every/dt)))==0) && ((i/dt) > 0)){
                int[] guess_indx = new int[num_examples];
                for (int j = 0; j < num_examples; j++) {
                    double max = 0;
                    int k_max = 0;
                    for (int k = 0; k < num_classes; k++) {
                        if (convnet.o_sum_spikes[k][j]>max){
                            max=convnet.o_sum_spikes[k][j];
                            k_max=k;
                        }
                    }
                    guess_indx[j]=k_max;
                }
                int correct = 0;
                for (int j = 0; j < test_y.length; j++) {
                    if (guess_indx[j]==test_y[j]){
                        correct++;
                    }
                }
                accuracy[acc_indx]=(float) correct/num_examples;
                System.out.println("Time: "+i+" s  Accuracy: "+accuracy[acc_indx]*100+"%");
            }
        }

        convnet.acc_snn=accuracy;

        return convnet;
    }
}
