package de.nanoimaging.deconv;

import android.content.res.AssetManager;
import android.util.Log;


public class FrozenmodelModel {


    /**** user load model manager sync interfaces ****/
    public static int load(AssetManager mgr){
            return ModelManager.loadModelSync("Frozenmodel", mgr);
    }

    public static float[] predict(float[] buf){
        return ModelManager.runModelSync("Frozenmodel",buf);
    }

    public static int unload(){
        return ModelManager.unloadModelSync();
    }


    /**** load user model async interfaces ****/
    public static int registerListenerJNI(ModelManagerListener listener){
        return ModelManager.registerListenerJNI(listener);
    }

    public static void loadAsync(AssetManager mgr){
        ModelManager.loadModelAsync("Frozenmodel", mgr);
    }

    public static void predictAsync(float[] buf) {
        ModelManager.runModelAsync("Frozenmodel",buf);
    }

    public static void unloadAsync(){
        ModelManager.unloadModelAsync();
    }
}
