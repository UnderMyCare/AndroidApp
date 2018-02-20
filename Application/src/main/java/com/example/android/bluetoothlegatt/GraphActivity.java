package com.example.android.bluetoothlegatt;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.helper.DateAsXAxisLabelFormatter;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.DataPointInterface;
import com.jjoe64.graphview.series.LineGraphSeries;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;

public class GraphActivity extends AppCompatActivity {
    SQLiteDatabase db;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_graph);
        db = openOrCreateDatabase("UnderMyCareData",MODE_PRIVATE,null);
        GraphView tempGraph = findViewById(R.id.tempGraph);
        GraphView heartGraph = findViewById(R.id.heartGraph);
        ArrayList<DataPoint> heartData = new ArrayList<>();
        ArrayList<DataPoint> tempData = new ArrayList<>();
        Cursor resultSet = db.rawQuery("SELECT HeartBeat, Temperature, added FROM Data WHERE added >= datetime('now', '-24 hour');",null);
        int i = 0;
        Log.e("NumberOfColumns", resultSet.getColumnCount()+" "+resultSet.getCount());
        tempGraph.getViewport().setXAxisBoundsManual(true);
        heartGraph.getViewport().setXAxisBoundsManual(true);
        resultSet.moveToFirst();
        if(resultSet.getCount()>0){

            long minx = Timestamp.valueOf(resultSet.getString(2)).getTime();
            tempGraph.getViewport().setMinX(minx);
            heartGraph.getViewport().setMinX(minx);
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");
            tempGraph.getGridLabelRenderer().setLabelFormatter(new DateAsXAxisLabelFormatter(getApplicationContext(), sdf));
            tempGraph.getGridLabelRenderer().setNumHorizontalLabels(3); // only 4 because of the space
            heartGraph.getGridLabelRenderer().setLabelFormatter(new DateAsXAxisLabelFormatter(getApplicationContext(), sdf));
            heartGraph.getGridLabelRenderer().setNumHorizontalLabels(3); // only 4 because of the space
                    while(i<resultSet.getCount()){
                i++;
                long added = Timestamp.valueOf(resultSet.getString(2)).getTime();
                heartData.add(new DataPoint(added, resultSet.getInt(0)));
                tempData.add(new DataPoint(added, resultSet.getFloat(1)));
                tempGraph.getViewport().setMaxX(added);
                heartGraph.getViewport().setMaxX(added);
                Log.e("Time", Timestamp.valueOf(resultSet.getString(2)).toString());
                resultSet.moveToNext();
            }
            resultSet.close();
            LineGraphSeries tempSeries = new LineGraphSeries<>(( tempData.toArray(new DataPointInterface[tempData.size()])));
            tempSeries.setColor(Color.BLUE);
            tempSeries.setDrawDataPoints(true);
            tempSeries.setDataPointsRadius(8);
            tempSeries.setTitle("Temperature (°C)");

            LineGraphSeries heartSeries = new LineGraphSeries<>(( heartData.toArray(new DataPointInterface[heartData.size()])));
            heartSeries.setColor(Color.RED);
            heartSeries.setDrawDataPoints(true);
            heartSeries.setDataPointsRadius(8);
            heartSeries.setTitle("Heart Rate (bpm)");

            tempGraph.addSeries(tempSeries);
            heartGraph.addSeries(heartSeries);
        }

//        graph.getLegendRenderer().setVisible(true);
//        graph.getLegendRenderer().setAlign(LegendRenderer.LegendAlign.TOP);
    }
}
