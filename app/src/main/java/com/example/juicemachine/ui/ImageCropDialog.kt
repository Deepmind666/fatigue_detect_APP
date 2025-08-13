package com.example.juicemachine.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest

@Composable
fun ImageCropDialog(
    imageUri: Uri,
    onCropComplete: (Uri) -> Unit,
    onDismiss: () -> Unit
) {
    var scale by remember { mutableStateOf(1f) }
    var rotation by remember { mutableStateOf(0f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // 标题栏
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "图片编辑",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "关闭")
                    }
                }

                // 图片编辑区域
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(16.dp)
                        .background(Color.Black.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                        .clip(RoundedCornerShape(8.dp))
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(imageUri)
                            .build(),
                        contentDescription = "编辑图片",
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                rotationZ = rotation,
                                translationX = offsetX,
                                translationY = offsetY
                            )
                            .pointerInput(Unit) {
                                detectTransformGestures { _, pan, zoom, rotationChange ->
                                    scale *= zoom
                                    rotation += rotationChange
                                    offsetX += pan.x
                                    offsetY += pan.y
                                }
                            },
                        contentScale = ContentScale.Fit
                    )
                }

                // 控制按钮
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // 旋转按钮
                    IconButton(
                        onClick = { rotation -= 90f }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.RotateLeft, contentDescription = "向左旋转")
                    }
                     
                     IconButton(
                         onClick = { rotation += 90f }
                     ) {
                         Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = "向右旋转")
                     }

                    // 重置按钮
                    Button(
                        onClick = {
                            scale = 1f
                            rotation = 0f
                            offsetX = 0f
                            offsetY = 0f
                        }
                    ) {
                        Text("重置")
                    }

                    // 确认按钮
                    Button(
                        onClick = {
                            // 应用变换并返回处理后的URI
                            // 注意：这里简化处理，实际应用中可能需要更复杂的图片处理
                            android.util.Log.d("ImageCropDialog", "裁剪完成 - scale: $scale, rotation: $rotation, offset: ($offsetX, $offsetY)")
                            onCropComplete(imageUri)
                            onDismiss()
                        }
                    ) {
                        Text("确认")
                    }
                }
            }
        }
    }
}

@Composable
fun ImageScaleDialog(
    imageUri: Uri,
    onScaleComplete: (Uri) -> Unit,
    onDismiss: () -> Unit
) {
    var scale by remember { mutableStateOf(1f) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(550.dp),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // 标题栏
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "图片缩放",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "关闭")
                    }
                }

                // 图片预览区域
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .padding(horizontal = 16.dp)
                        .background(Color.Black.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                        .clip(RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(imageUri)
                            .build(),
                        contentDescription = "缩放预览",
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(scaleX = scale, scaleY = scale),
                        contentScale = ContentScale.Fit
                    )
                }

                // 缩放控制
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text("缩放比例: ${String.format("%.1f", scale)}x")
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Slider(
                        value = scale,
                        onValueChange = { scale = it },
                        valueRange = 0.1f..3f,
                        steps = 29
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 预设缩放按钮
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Button(
                            onClick = { scale = 0.5f },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("50%")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { scale = 1f },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("100%")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { scale = 2f },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("200%")
                        }
                    }
                }

                // 确认按钮
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    OutlinedButton(onClick = onDismiss) {
                        Text("取消")
                    }
                    Button(
                        onClick = {
                            // 应用缩放并返回处理后的URI
                            android.util.Log.d("ImageScaleDialog", "缩放完成 - scale: $scale")
                            onScaleComplete(imageUri)
                            onDismiss()
                        }
                    ) {
                        Text("确认")
                    }
                }
            }
        }
    }
}