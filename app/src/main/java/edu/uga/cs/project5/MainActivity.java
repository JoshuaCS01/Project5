package edu.uga.cs.project5;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import edu.uga.cs.project5.AuthActivity;
import edu.uga.cs.project5.R;

public class MainActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private BottomNavigationView bottomNav;
    private FragmentManager fm;
    private static final String SELECTED_TAB_KEY = "selected_tab";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main); // see xml below

        mAuth = FirebaseAuth.getInstance();
        fm = getSupportFragmentManager();

        bottomNav = findViewById(R.id.bottom_navigation);
        bottomNav.setOnItemSelectedListener(item -> {
            switchFragment(item.getItemId());
            return true;
        });

        // Restore selected tab (or default to browse)
        int selected = R.id.nav_browse;
        if (savedInstanceState != null) selected = savedInstanceState.getInt(SELECTED_TAB_KEY, R.id.nav_browse);
        bottomNav.setSelectedItemId(selected); // triggers listener -> fragment swap

        TextView tvTitle = findViewById(R.id.tvTitle);
        tvTitle.setOnClickListener(v -> logout());

    }

    @Override
    protected void onStart() {
        super.onStart();
        // If user not logged in, send to AuthActivity
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            Intent i = new Intent(this, AuthActivity.class);
            startActivity(i);
            finish();
        }
    }

    private void switchFragment(int itemId) {
        Fragment current = fm.findFragmentById(R.id.fragment_container);
        Fragment to = null;
        String tag = null;

        if (itemId == R.id.nav_browse) {
            tag = "browse";
            to = fm.findFragmentByTag(tag);
            if (to == null) to = new CategoryListFragment(); // implement later
        } else if (itemId == R.id.nav_myitems) {
            tag = "myitems";
            to = fm.findFragmentByTag(tag);
            if (to == null) to = new MyItemsFragment(); // implement later
        } else if (itemId == R.id.nav_tx) {
            tag = "transactions";
            to = fm.findFragmentByTag(tag);
            if (to == null) to = new TransactionsFragment(); // implement later
        }

        if (to != null && to != current) {
            fm.beginTransaction()
                    .replace(R.id.fragment_container, to, tag)
                    .commit();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu); // sign out menu
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_signout) {
            mAuth.signOut();
            // Return to AuthActivity
            Intent i = new Intent(this, AuthActivity.class);
            startActivity(i);
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(SELECTED_TAB_KEY, bottomNav.getSelectedItemId());
    }

    private void logout() {
        FirebaseAuth.getInstance().signOut();
        Intent intent = new Intent(MainActivity.this, AuthActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }
}
