package ua.dp.budash.mika.firebaseinnovecs;

import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private DatabaseReference rootReference = FirebaseDatabase.getInstance().getReference();
    private DatabaseReference messagesReference = rootReference.child("messages");
    private DatabaseReference filterWordsReference = rootReference.child("words");

    private static Handler uiHandler = new Handler();
    private static Handler processingHandler;

    private List<ProcessedMessage> processedMessages = new ArrayList<>();
    private List<String> waitingMessages = new ArrayList<>();

    private String[] currentIgnoreWords;

    private ChatAdapter chatAdapter;
    private RecyclerView listView;
    private TextView wordsView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        listView = (RecyclerView) findViewById(R.id.list);
        listView.setLayoutManager(new LinearLayoutManager(getBaseContext(), LinearLayoutManager.VERTICAL, false));

        chatAdapter = new ChatAdapter();
        listView.setAdapter(chatAdapter);

        wordsView = (TextView) findViewById(R.id.words);
        if (processingHandler == null) {
            HandlerThread handlerThread = new HandlerThread(getClass().getSimpleName());
            handlerThread.start();
            processingHandler = new Handler(handlerThread.getLooper());
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        messagesReference.addValueEventListener(messageEventListener);
        filterWordsReference.addValueEventListener(wordsEventListener);
    }

    @Override
    protected void onStop() {
        super.onStop();
        messagesReference.removeEventListener(messageEventListener);
        filterWordsReference.removeEventListener(wordsEventListener);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (processingHandler != null) {
            processingHandler.getLooper().quit();
            processingHandler = null;
        }
    }

    private static class ProcessedMessage {
        final String preProcessed;
        final String postProcessed;

        public ProcessedMessage(String preProcessed, String postProcessed) {
            this.preProcessed = preProcessed;
            this.postProcessed = postProcessed;
        }
    }

    private void processMessage(final String message) {
        if (currentIgnoreWords == null) {
            waitingMessages.add(message);
        } else {
            processingHandler.post(new Runnable() {
                @Override
                public void run() {
                    final String converted = Utils.filter(message, currentIgnoreWords);

                    uiHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (!isFinishing()) {
                                processedMessages.add(new ProcessedMessage(message, converted));
                                chatAdapter.notifyItemInserted(processedMessages.size() - 1);
                            }
                        }
                    });
                }
            });
        }
    }

    private class ChatAdapter extends RecyclerView.Adapter<ChatViewHolder> {

        @Override
        public ChatViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new ChatViewHolder(getLayoutInflater().inflate(R.layout.chat_item, parent, false));
        }

        @Override
        public int getItemCount() {
            return processedMessages.size();
        }

        @Override
        public void onBindViewHolder(ChatViewHolder holder, int position) {
            holder.bind(position);
        }

    }

    private class ChatViewHolder extends RecyclerView.ViewHolder {

        TextView originalView;
        TextView convertedView;

        public ChatViewHolder(View itemView) {
            super(itemView);
            originalView = (TextView) itemView.findViewById(R.id.original);
            convertedView = (TextView) itemView.findViewById(R.id.converted);
        }

        void bind(int position) {
            if (position < 0 || position >= processedMessages.size()) {
                return;
            }
            ProcessedMessage processedMessage = processedMessages.get(position);
            originalView.setText(processedMessage.preProcessed);
            convertedView.setText(processedMessage.postProcessed);
        }
    }

    private ValueEventListener messageEventListener = new ValueEventListener() {
        @Override
        public void onDataChange(DataSnapshot dataSnapshot) {
            String value = dataSnapshot.getValue(String.class);
            processMessage(value);
        }

        @Override
        public void onCancelled(DatabaseError databaseError) {
            Toast.makeText(getBaseContext(), databaseError.toString(), Toast.LENGTH_LONG).show();
        }
    };

    private ValueEventListener wordsEventListener = new ValueEventListener() {
        @Override
        public void onDataChange(DataSnapshot dataSnapshot) {
            String value = dataSnapshot.getValue(String.class);
            wordsView.setText(value);
            boolean wasInit = currentIgnoreWords != null;

            currentIgnoreWords = value.replaceAll("[\\s\\W]+", ",").split(",");
            if (!wasInit) {
                for (String s : waitingMessages) {
                    processMessage(s);
                }
                waitingMessages.clear();
            }
        }

        @Override
        public void onCancelled(DatabaseError databaseError) {
            Toast.makeText(getBaseContext(), databaseError.toString(), Toast.LENGTH_LONG).show();
        }
    };

}
