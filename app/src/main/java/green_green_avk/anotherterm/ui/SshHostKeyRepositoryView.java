package green_green_avk.anotherterm.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.jcraft.jsch.JSch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import green_green_avk.anotherterm.R;
import green_green_avk.anotherterm.utils.SshHostKey;
import green_green_avk.anotherterm.utils.SshHostKeyRepository;

public class SshHostKeyRepositoryView extends RecyclerView {
    public SshHostKeyRepositoryView(@NonNull final Context context) {
        super(context);
        init();
    }

    public SshHostKeyRepositoryView(@NonNull final Context context,
                                    @Nullable final AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public SshHostKeyRepositoryView(@NonNull final Context context,
                                    @Nullable final AttributeSet attrs, final int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    protected void init() {
        setLayoutManager(new LinearLayoutManager(getContext()));
        setAdapter(new Adapter(getContext()));
    }

    protected static class ViewHolder extends RecyclerView.ViewHolder {
        public ViewHolder(@NonNull final View v) {
            super(v);
        }
    }

    protected static class Adapter extends RecyclerView.Adapter<ViewHolder> {
        protected final SshHostKeyRepository repo;
        protected final JSch jSch = new JSch();
        protected List<SshHostKey> keys;

        public Adapter(@NonNull final Context ctx) {
            super();
            repo = new SshHostKeyRepository(ctx);
            refreshKeys();
        }

        @NonNull
        private String getFingerPrintSortKey(final SshHostKey key) {
            String r;
            try {
                r = key.getFingerPrint(jSch);
            } catch (final RuntimeException e) {
                r = null;
            }
            return r == null ? "" : r;
        }

        protected void refreshKeys() {
            keys = new ArrayList<>(repo.getHostKeySet());
            Collections.sort(keys, (o1, o2) ->
                    (o1.getHost() + o1.getType() + getFingerPrintSortKey(o1))
                            .compareTo(o2.getHost() + o2.getType() + getFingerPrintSortKey(o2)));
        }

        @Override
        @NonNull
        public ViewHolder onCreateViewHolder(@NonNull final ViewGroup parent, final int viewType) {
            final View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.ssh_host_key_repository_entry, parent, false);
            final View bMenu = v.findViewById(R.id.b_menu);
            UiUtils.setShowContextMenuOnClick(bMenu);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull final ViewHolder holder, final int position) {
            final SshHostKey key = keys.get(position);
            final TextView hostnameView = holder.itemView.findViewById(R.id.f_hostname);
            final TextView typeView = holder.itemView.findViewById(R.id.f_type);
            final TextView fingerprintView = holder.itemView.findViewById(R.id.f_fingerprint);
            hostnameView.setText(key.getHost());
            typeView.setText(key.getType());
            String fp;
            try {
                fp = key.getFingerPrint(jSch);
            } catch (final RuntimeException e) {
                fp = holder.itemView.getContext()
                        .getString(R.string.msg_desc_obj_cannot_obtain_fingerprint);
            }
            fingerprintView.setText(fp);
            holder.itemView.findViewById(R.id.b_menu)
                    .setOnCreateContextMenuListener((menu, v, menuInfo) ->
                            menu.add(0, R.string.action_delete,
                                            0, R.string.action_delete)
                                    .setOnMenuItemClickListener(item -> {
                                        switch (item.getItemId()) {
                                            case R.string.action_delete:
                                                repo.remove(key);
                                                refreshKeys();
                                                notifyDataSetChanged();
                                                return true;
                                        }
                                        return false;
                                    }));
        }

        @Override
        public int getItemCount() {
            return keys.size();
        }
    }
}
