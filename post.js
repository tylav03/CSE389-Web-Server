function submit(){
    fetch("localhost:8080", {
        method: "POST",
        body: JSON.stringify({
          userId: 1
        }),
        headers: {
          "Content-type": "application/json; charset=UTF-8"
        }
    });
}