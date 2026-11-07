#include <iostream>
#include <sstream>
#include <string>

#include "zeravrix/ipc/ipc_client.hpp"

int main() {
    std::istringstream input("{\"protocol\":1,\"requestId\":\"native-smoke\",\"ok\":true}\n");
    std::ostringstream output;
    zeravrix::ipc::StreamClient client(input, output);
    if (!client.connect()) return 1;
    if (!client.send_frame("{\"protocol\":1,\"requestId\":\"native-smoke\",\"op\":\"hello\"}")) return 2;
    const auto response = client.receive_frame();
    client.close();
    if (!response || response->find("\"protocol\":1") == std::string::npos) return 3;
    std::cout << *response << '\n';
    return 0;
}
