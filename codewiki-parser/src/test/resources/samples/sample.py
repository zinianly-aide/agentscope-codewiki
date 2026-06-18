class Greeter:
    """A friendly greeter."""

    def __init__(self, name):
        self.name = name

    def greet(self, greeting="Hello"):
        return f"{greeting}, {self.name}!"

def create_greeter(name):
    """Factory function."""
    return Greeter(name)

def format_message(text):
    return text.strip().upper()
