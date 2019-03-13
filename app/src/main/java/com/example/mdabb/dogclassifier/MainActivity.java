package com.example.mdabb.dogclassifier;

import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.RadioButton;
import android.widget.RadioGroup;

public class MainActivity extends AppCompatActivity {
    public static final String MODEL = "com.example.mdabb.dogclassifier.MODEL";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main2);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        RecyclerView mRecyclerView = (RecyclerView) findViewById(R.id.my_recycler_view);
//        mRecyclerView.setHasFixedSize(true);

        // use a linear layout manager
        RecyclerView.LayoutManager mLayoutManager = new LinearLayoutManager(this);
        mRecyclerView.setLayoutManager(mLayoutManager);

        // specify an adapter (see also next example)
        RecyclerView.Adapter mAdapter = new MyRecyclerViewAdapter(null);
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

    public void checkButton(View v) {
    }

    public void onCameraButtonClick(View view) {
        final Intent intent = new Intent(this, CameraActivity.class);
        //get selected model
        RadioGroup radioGroup = (RadioGroup) findViewById(R.id.radioModel);
        RadioButton radioButton = (RadioButton) findViewById(radioGroup.getCheckedRadioButtonId());
        String model = radioButton.getText().toString();
        //start activity
        intent.putExtra(MODEL, model);
        startActivity(intent);
    }
}
