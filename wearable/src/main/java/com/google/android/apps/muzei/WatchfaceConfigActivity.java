/*
 * Copyright 2017 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.apps.muzei;

import android.os.Bundle;
import android.support.v7.widget.RecyclerView;
import android.support.wearable.activity.WearableActivity;
import android.support.wearable.view.WearableRecyclerView;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.Switch;

import net.nurik.roman.muzei.R;

public class WatchfaceConfigActivity extends WearableActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.watchface_config_activity);
        WearableRecyclerView wearableRecyclerView = findViewById(R.id.config_recycler_view);
        wearableRecyclerView.setCenterEdgeItems(true);
        wearableRecyclerView.setAdapter(new ConfigAdapter());
        setAmbientEnabled();
    }

    private class ConfigAdapter extends RecyclerView.Adapter {
        private final LayoutInflater mInflater;
        ConfigAdapter() {
            mInflater = LayoutInflater.from(WatchfaceConfigActivity.this);
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(final ViewGroup parent, final int viewType) {
            switch (viewType) {
                case 0:
                    return new RecyclerView.ViewHolder(
                            mInflater.inflate(R.layout.watchface_preview, parent, false)) {};
                case 1:
                    return new RecyclerView.ViewHolder(new Switch(WatchfaceConfigActivity.this)) {};
                default:
                    return null;
            }
        }

        @Override
        public void onBindViewHolder(final RecyclerView.ViewHolder viewHolder, final int position) {
            switch (position) {
                case 0:
                    break;
                case 1:
                    break;
            }
        }

        @Override
        public int getItemViewType(final int position) {
            if (position == 0) {
                return 0;
            } else {
                return 1;
            }
        }

        @Override
        public int getItemCount() {
            return 2;
        }
    }
}
