package com.example.mdabb.dogclassifier;

import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {
    private RadioGroup radioGroup;
    private RadioButton radioButton;
    private RecyclerView mRecyclerView;
    private RecyclerView.Adapter mAdapter;
    private RecyclerView.LayoutManager mLayoutManager;
    public static final String MODEL ="com.example.mdabb.dogclassifier.MODEL";
    public static final String USEGPU ="com.example.mdabb.dogclassifier.USEGPU";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main2);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        mRecyclerView = (RecyclerView) findViewById(R.id.my_recycler_view);
//        mRecyclerView.setHasFixedSize(true);

        // use a linear layout manager
        mLayoutManager = new LinearLayoutManager(this);
        mRecyclerView.setLayoutManager(mLayoutManager);

        // specify an adapter (see also next example)
        mAdapter = new MyRecyclerViewAdapter(null);
        mRecyclerView.setAdapter(mAdapter);
        mRecyclerView.setKeepScreenOn(true);

        // setting up response to camera launch button
        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onCameraButtonClick(view);

            }
        });
    }
    public void checkButton(View v){
        radioButton = ((RadioButton)v);
        if(radioButton.getId() ==R.id.radio_two_Proc){
            if(!GpuDelegateHelper.isGpuDelegateAvailable()){
                Toast.makeText(this,"gpu not in this build.",Toast.LENGTH_SHORT).show();
                ((RadioButton)findViewById(R.id.radio_one_Proc)).setChecked(true);
                radioButton.setChecked(false);
                radioButton.setActivated(false);
            }
        }
    }
    public void onCameraButtonClick(View view){
        final Intent intent = new Intent(this, CameraActivity.class);
        //get selected model
        radioGroup=(RadioGroup) findViewById(R.id.radioModel);
        radioButton=(RadioButton) findViewById(radioGroup.getCheckedRadioButtonId());
        String model=radioButton.getText().toString();
        //get selected Proc
        radioGroup=(RadioGroup) findViewById(R.id.radioProc);
        radioButton=(RadioButton) findViewById(radioGroup.getCheckedRadioButtonId());
        Boolean useGpu=(radioButton.getText().toString()=="GPU");
        //start activity
        intent.putExtra(MODEL,model);
        intent.putExtra(USEGPU,useGpu);
        startActivity(intent);
    }
}
