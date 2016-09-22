# BLE-Mesh
demo视频可见（http://pan.baidu.com/s/1eRF9DdC）

传统的对等通信（Wi-Fi 直连、蓝牙）只能支持一对一的通信，无法扩展。
本项目基于Bluetooth Low Energy (BLE)，通过扩展实现多跳转发机制，组建Mesh网络，使得设备可以同时和周围多台设备通信。 
folk from https://github.com/OnlyInAmerica/BLEMeshChat（该项目没有能实现多跳的Mesh网络）

本项目主要功能包括： 

( 1 ). 基于BLE进行设备发现，

( 2 ). 利用BLE连接，设备之间分享所知的其他节点信息，形成Mesh网络，

( 3 ). 利用图最短路径算法，构建路由机制，实现消息在蓝牙设备之间的多跳转发。 
利用BLE Mesh构建上层聊天应用，用户不联网，直接通过蓝牙BLE就可以和周边的设备聊天。（包括直连设备和多跳设备）

An BLE based mesh netowork platform for android

folk from https://github.com/OnlyInAmerica/BLEMeshChat
BLEMeshChat don't support multi hop well.
each device only connect to around every other devices to from a mesh net.
which don't support goal directed connection.

This project hope to realize that device can form a multi hop net, and choose specific remote device which is reachable by one hop or multi hops.
and then communicate with that device.

#Screenshots
1. 发现周围节点，包括直连设备和多跳连接的设备
1. Find nearby users, including direct connect remote peer, and multi-hop connect peer.
![image](https://github.com/wl1244hotmai/BLE-Mesh/blob/master/IMG_7606.MOV_20160909_225617.566.jpg)

2. 与其他用户聊天
2. Chat with any one of them, if multi-hop,then message will forward through middle node to the destination.
![image](https://github.com/wl1244hotmai/BLE-Mesh/blob/master/IMG_7606.MOV_20160909_225930.166.jpg)


