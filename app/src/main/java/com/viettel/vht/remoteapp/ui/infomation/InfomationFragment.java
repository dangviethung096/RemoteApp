package com.viettel.vht.remoteapp.ui.infomation;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.viettel.vht.remoteapp.R;


public class InfomationFragment extends Fragment {

    private SwipeRefreshLayout swipeRefreshLayout;
    private boolean isAqiShow = false;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {

        View root = inflater.inflate(R.layout.fragment_information, container, false);

        swipeRefreshLayout = getActivity().findViewById(R.id.swipe_refresh);
        swipeRefreshLayout.setEnabled(false);

        return root;
    }
}
