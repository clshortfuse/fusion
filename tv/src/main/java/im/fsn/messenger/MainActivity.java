package im.fsn.messenger;

/**
 * Created by Carlos on 10/15/2014.
 */
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/*
 * A wrapper class for main view of the app
 */
public class MainActivity extends Activity {
    /** Called when the activity is first created. */

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }
}