package edu.uga.cs.project5;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class PendingTransactionsFragment extends Fragment {

    public PendingTransactionsFragment() { }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_simple, container, false);
        TextView title = root.findViewById(R.id.tvPlaceholderTitle);
        TextView subtitle = root.findViewById(R.id.tvPlaceholderSubtitle);
        title.setText("Pending Transactions — Placeholder");
        subtitle.setText("This will show pending transactions.");
        return root;
    }
}
